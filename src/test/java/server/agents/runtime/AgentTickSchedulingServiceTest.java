package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentQuiescenceReason;
import server.agents.runtime.scheduler.AgentQuiescenceToken;
import server.agents.runtime.scheduler.AgentSchedulerMode;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulingServiceTest {
    @AfterEach
    void clearFlag() {
        System.clearProperty("agents.scheduler.central.enabled");
        AgentRuntimeRegistry.clear();
    }

    @Test
    void disabledCentralSchedulerPreservesLegacyRegistrationPath() {
        System.clearProperty("agents.scheduler.central.enabled");
        AgentRuntimeEntry entry = activeEntry(101);
        ScheduledFuture<?> expected = mock(ScheduledFuture.class);
        AtomicBoolean legacyCalled = new AtomicBoolean();

        AgentScheduleHandle actual = AgentTickSchedulingService.register(
                entry,
                () -> { },
                50L,
                (tick, period) -> {
                    legacyCalled.set(true);
                    return expected;
                });

        assertTrue(legacyCalled.get());
        assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, actual.mode());
        assertEquals(entry.sessionGeneration(), actual.sessionId().generation());
    }

    @Test
    void legacyRegistrationUsesTheSameQuiescenceGuard() {
        System.clearProperty("agents.scheduler.central.enabled");
        AgentRuntimeEntry entry = activeEntry(101);
        ScheduledFuture<?> expected = mock(ScheduledFuture.class);
        AtomicReference<Runnable> scheduledTick = new AtomicReference<>();
        AtomicInteger gameplayTicks = new AtomicInteger();

        AgentScheduleHandle handle = AgentTickSchedulingService.register(
                entry,
                gameplayTicks::incrementAndGet,
                50L,
                (tick, period) -> {
                    scheduledTick.set(tick);
                    return expected;
                });
        CompletableFuture<AgentQuiescenceToken> result = handle.quiesce(
                AgentQuiescenceReason.MAINTENANCE,
                Duration.ofSeconds(5)).toCompletableFuture();

        scheduledTick.get().run();

        AgentQuiescenceToken token = result.join();
        assertEquals(0, gameplayTicks.get());
        assertTrue(handle.isQuiescent());
        assertTrue(handle.resume(token));
        scheduledTick.get().run();
        assertEquals(1, gameplayTicks.get());
    }

    private static AgentRuntimeEntry activeEntry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(1, entry);
        return entry;
    }
}
