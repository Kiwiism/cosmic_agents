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

import java.util.ArrayDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerSliceContinuationTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void incompleteFrameQueuesImmediateOwnerShardContinuation() {
        AtomicLong now = new AtomicLong(1_000L);
        ArrayDeque<Runnable> continuations = new ArrayDeque<>();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AgentTickScheduler scheduler = new AgentTickScheduler(
                now::get,
                System::nanoTime,
                (task, period) -> future,
                (task, delay) -> {
                    continuations.addLast(task);
                    return future;
                },
                config(),
                0);
        AgentRuntimeEntry entry = activeEntry();
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
        scheduler.register(entry, tick, 50L);

        scheduler.tickAll();

        assertEquals(1, slices.get());
        assertTrue(entry.tickSliceState().continuationPending());
        assertEquals(1, continuations.size());

        continuations.removeFirst().run();

        assertEquals(2, slices.get());
        assertFalse(entry.tickSliceState().continuationPending());
        assertEquals(0, continuations.size());
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

    private static AgentSchedulerConfig config() {
        return new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SEQUENTIAL,
                50L,
                true,
                250L,
                0,
                8,
                1_000L,
                8,
                40,
                10,
                2_000L,
                1);
    }

    private static AgentRuntimeEntry activeEntry() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(101);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(1, entry);
        return entry;
    }
}
