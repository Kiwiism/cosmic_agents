package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTickSlicingServiceTest {
    @Test
    void runsBoundedTurnsAndFinalizesOnlyAfterLastSlice() {
        AgentTickSliceState state = new AgentTickSliceState();
        state.configure(true, 2, 8);
        List<String> calls = new ArrayList<>();
        AgentTickSlicingService.Hooks hooks = hooks(calls, new FourSliceFrame(calls));
        AtomicLong nanoTime = new AtomicLong();

        AgentTickSlicingService.TurnResult first =
                AgentTickSlicingService.runTurn(state, hooks, () -> nanoTime.getAndAdd(10L));

        assertFalse(first.frameComplete());
        assertTrue(first.continuationPending());
        assertEquals(2, first.slicesRun());
        assertEquals(List.of("begin", "mailbox", "PREFLIGHT", "LIFECYCLE"), calls);

        AgentTickSlicingService.TurnResult second =
                AgentTickSlicingService.runTurn(state, hooks, () -> nanoTime.getAndAdd(10L));

        assertTrue(second.frameComplete());
        assertFalse(second.continuationPending());
        assertEquals(2, second.slicesRun());
        assertEquals(40L, second.frameExecutionNs());
        assertEquals(List.of(
                "begin",
                "mailbox",
                "PREFLIGHT",
                "LIFECYCLE",
                "PLAN_AND_GATES",
                "CAPABILITY_AND_MOVEMENT",
                "settle",
                "reset"), calls);
        assertNull(state.frame());
    }

    @Test
    void sliceFailureClearsFrameWithoutSettlingAndInvokesFailureHandler() {
        AgentTickSliceState state = new AgentTickSliceState();
        state.configure(true, 2, 8);
        List<String> calls = new ArrayList<>();
        AgentTickFrame failingFrame = new AgentTickFrame() {
            @Override
            public AgentTickSliceResult runNextSlice() {
                calls.add("slice");
                throw new IllegalStateException("failed slice");
            }

            @Override
            public boolean isComplete() {
                return false;
            }
        };

        AgentTickSlicingService.TurnResult result = AgentTickSlicingService.runTurn(
                state,
                hooks(calls, failingFrame),
                System::nanoTime);

        assertTrue(result.failed());
        assertEquals(List.of("begin", "mailbox", "slice", "failure"), calls);
        assertNull(state.frame());
        assertFalse(state.continuationPending());
    }

    @Test
    void continuationLimitAbortsAFrameThatCannotFinish() {
        AgentTickSliceState state = new AgentTickSliceState();
        state.configure(true, 1, 1);
        List<String> calls = new ArrayList<>();
        AgentTickSlicingService.Hooks hooks = hooks(calls, new FourSliceFrame(calls));

        AgentTickSlicingService.TurnResult first =
                AgentTickSlicingService.runTurn(state, hooks, System::nanoTime);
        AgentTickSlicingService.TurnResult second =
                AgentTickSlicingService.runTurn(state, hooks, System::nanoTime);

        assertTrue(first.continuationPending());
        assertTrue(second.failed());
        assertEquals("failure", calls.get(calls.size() - 1));
        assertNull(state.frame());
    }

    private static AgentTickSlicingService.Hooks hooks(List<String> calls, AgentTickFrame frame) {
        return new AgentTickSlicingService.Hooks(
                () -> calls.add("begin"),
                () -> calls.add("mailbox"),
                () -> frame,
                () -> calls.add("settle"),
                () -> calls.add("reset"),
                failure -> calls.add("failure"));
    }

    private static final class FourSliceFrame implements AgentTickFrame {
        private final AtomicInteger next = new AtomicInteger();
        private final List<String> calls;

        private FourSliceFrame(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public AgentTickSliceResult runNextSlice() {
            AgentTickSliceKind slice = AgentTickSliceKind.values()[next.getAndIncrement()];
            calls.add(slice.name());
            boolean complete = next.get() == AgentTickSliceKind.values().length;
            return new AgentTickSliceResult(
                    slice,
                    complete ? AgentTickNextRunHint.NORMAL_CADENCE : AgentTickNextRunHint.IMMEDIATE_CONTINUATION,
                    complete);
        }

        @Override
        public boolean isComplete() {
            return next.get() == AgentTickSliceKind.values().length;
        }
    }
}
