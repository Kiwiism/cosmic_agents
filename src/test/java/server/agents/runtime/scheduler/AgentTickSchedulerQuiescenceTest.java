package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentTickFrame;
import server.agents.runtime.AgentTickNextRunHint;
import server.agents.runtime.AgentTickSliceKind;
import server.agents.runtime.AgentTickSliceResult;
import server.agents.runtime.AgentTickSlicingService;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerQuiescenceTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void centralRegistrationQuiescesWithoutRunningGameplayAndResumesWithToken() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentRuntimeEntry entry = activeEntry(101);
        AtomicInteger gameplayTicks = new AtomicInteger();
        AgentScheduleHandle handle = scheduler.register(entry, gameplayTicks::incrementAndGet, 50L);

        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.PROFILE_EXCHANGE,
                Duration.ofSeconds(5)).toCompletableFuture();
        scheduler.tickAll();

        AgentQuiescenceToken token = result.join();
        assertEquals(0, gameplayTicks.get());
        assertTrue(handle.isQuiescent());
        assertTrue(handle.validatesQuiescence(token));
        assertEquals(0, scheduler.scheduledRegistrationCount());

        assertTrue(handle.resume(token));
        scheduler.tickAll();
        assertEquals(1, gameplayTicks.get());
        assertFalse(handle.isQuiescent());
    }

    @Test
    void quiescenceCanProgressWhileOrdinaryPauseRemainsSet() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentRuntimeEntry entry = activeEntry(101);
        AtomicInteger gameplayTicks = new AtomicInteger();
        AgentScheduleHandle handle = scheduler.register(entry, gameplayTicks::incrementAndGet, 50L);
        scheduler.pause(entry);

        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.CONSISTENT_SNAPSHOT,
                Duration.ofSeconds(5)).toCompletableFuture();
        scheduler.tickAll();

        AgentQuiescenceToken token = result.join();
        assertEquals(0, gameplayTicks.get());
        assertTrue(handle.resume(token));
        now.addAndGet(50L);
        scheduler.tickAll();
        assertEquals(0, gameplayTicks.get());

        scheduler.resume(entry);
        scheduler.tickAll();
        assertEquals(1, gameplayTicks.get());
    }

    @Test
    void quiescenceFinishesAnActiveBoundedFrameBeforeIssuingToken() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentRuntimeEntry entry = activeEntry(101);
        entry.tickSliceState().configure(true, 1, 8);
        AtomicInteger slices = new AtomicInteger();
        AgentTickFrame frame = twoSliceFrame(slices);
        Runnable tick = () -> AgentTickSlicingService.runTurn(
                entry.tickSliceState(),
                new AgentTickSlicingService.Hooks(
                        () -> { },
                        () -> { },
                        () -> frame,
                        () -> { },
                        () -> { },
                        () -> { },
                        failure -> { }));
        AgentScheduleHandle handle = scheduler.register(entry, tick, 50L);

        scheduler.tickAll();
        assertEquals(1, slices.get());
        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.CHARACTER_TRANSFER,
                Duration.ofSeconds(5)).toCompletableFuture();

        scheduler.tickAll();

        assertEquals(2, slices.get());
        assertFalse(entry.tickSliceState().frameActive());
        assertTrue(result.isDone());
        assertTrue(handle.isQuiescent());
    }

    @Test
    void cancellationFailsOutstandingQuiescenceInsteadOfReturningFakeToken() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentScheduleHandle handle = scheduler.register(activeEntry(101), () -> { }, 50L);
        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.RELEASE,
                Duration.ofSeconds(5)).toCompletableFuture();

        handle.cancel(false);

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals(AgentQuiescenceException.Reason.CLOSED,
                ((AgentQuiescenceException) failure.getCause()).reason());
    }

    @Test
    void unregisteredSessionFailsOutstandingQuiescenceBeforeTickPreparation() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentRuntimeEntry entry = activeEntry(101);
        AgentScheduleHandle handle = scheduler.register(entry, () -> { }, 50L);
        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.RELEASE,
                Duration.ofSeconds(5)).toCompletableFuture();

        AgentRuntimeRegistry.unregisterEntry(1, entry);
        scheduler.tickAll();

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals(AgentQuiescenceException.Reason.STALE_SESSION,
                ((AgentQuiescenceException) failure.getCause()).reason());
        assertFalse(entry.actionMailbox().ordinaryWorkFrozen());
    }

    @Test
    void publicBoundaryRequiresTheExactGenerationBoundToken() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentTickScheduler scheduler = scheduler(now);
        AgentRuntimeEntry entry = activeEntry(101);
        AgentScheduleHandle handle = scheduler.register(entry, () -> { }, 50L);
        entry.scheduledTaskState().attachScheduledTask(handle);
        CompletableFuture<AgentQuiescenceToken> result = AgentQuiescenceService.quiesce(
                entry,
                AgentQuiescenceReason.PROFILE_EXCHANGE,
                Duration.ofSeconds(5)).toCompletableFuture();
        scheduler.tickAll();
        AgentQuiescenceToken token = result.join();

        AgentQuiescenceService.requireValidToken(entry, token);
        AgentQuiescenceToken forged = new AgentQuiescenceToken(
                token.sessionId(),
                token.requestId() + 1L,
                token.reason(),
                token.completedAtMs());
        assertThrows(
                AgentQuiescenceException.class,
                () -> AgentQuiescenceService.requireValidToken(entry, forged));
        assertTrue(AgentQuiescenceService.resume(entry, token));
    }

    private static AgentTickScheduler scheduler(AtomicLong now) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                16,
                1_000L,
                16,
                40,
                10,
                2_000L,
                1);
        return new AgentTickScheduler(
                now::get,
                (task, period) -> future,
                (task, delay) -> future,
                config);
    }

    private static AgentRuntimeEntry activeEntry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(1, entry);
        return entry;
    }

    private static AgentTickFrame twoSliceFrame(AtomicInteger slices) {
        return new AgentTickFrame() {
            @Override
            public AgentTickSliceResult runNextSlice() {
                int slice = slices.incrementAndGet();
                boolean complete = slice == 2;
                return new AgentTickSliceResult(
                        complete ? AgentTickSliceKind.CAPABILITY_AND_MOVEMENT : AgentTickSliceKind.PREFLIGHT,
                        complete ? AgentTickNextRunHint.NORMAL_CADENCE
                                : AgentTickNextRunHint.IMMEDIATE_CONTINUATION,
                        complete);
            }

            @Override
            public boolean isComplete() {
                return slices.get() == 2;
            }
        };
    }
}
