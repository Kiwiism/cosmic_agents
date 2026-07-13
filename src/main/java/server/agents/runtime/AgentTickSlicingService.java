package server.agents.runtime;

import server.agents.monitoring.AgentSchedulerMetrics;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentTickSlicingService {
    public record Hooks(Runnable beginTick,
                        Runnable drainMailbox,
                        Supplier<AgentTickFrame> frameFactory,
                        Runnable settleMovement,
                        Runnable resetFailures,
                        Consumer<Throwable> failureHandler) {
        public Hooks {
            if (beginTick == null || drainMailbox == null || frameFactory == null
                    || settleMovement == null || resetFailures == null || failureHandler == null) {
                throw new IllegalArgumentException("Agent tick slicing hooks are required");
            }
        }
    }

    public record TurnResult(boolean frameComplete,
                             boolean continuationPending,
                             boolean failed,
                             int slicesRun,
                             long frameExecutionNs) {
    }

    private AgentTickSlicingService() {
    }

    public static TurnResult runTurn(AgentTickSliceState state, Hooks hooks) {
        return runTurn(state, hooks, System::nanoTime);
    }

    static TurnResult runTurn(AgentTickSliceState state, Hooks hooks, LongSupplier nanoTime) {
        if (state == null || hooks == null || nanoTime == null) {
            throw new IllegalArgumentException("Agent tick slicing dependencies are required");
        }
        int slicesRun = 0;
        try {
            if (state.frame() == null) {
                hooks.beginTick().run();
                hooks.drainMailbox().run();
                state.startFrame(hooks.frameFactory().get());
            }
            state.beginTurn();
            while (slicesRun < state.maxSlicesPerTurn()) {
                AgentTickFrame frame = state.frame();
                long started = nanoTime.getAsLong();
                AgentTickSliceResult result = frame.runNextSlice();
                long elapsedNs = Math.max(0L, nanoTime.getAsLong() - started);
                state.recordExecution(elapsedNs);
                AgentSchedulerMetrics.recordTickSlice(result.completedSlice(), elapsedNs);
                slicesRun++;
                if (result.frameComplete()) {
                    hooks.settleMovement().run();
                    hooks.resetFailures().run();
                    long frameExecutionNs = state.accumulatedExecutionNs();
                    state.clear();
                    return new TurnResult(true, false, false, slicesRun, frameExecutionNs);
                }
            }
            state.requestContinuation();
            AgentSchedulerMetrics.recordTickContinuation();
            return new TurnResult(false, true, false, slicesRun, state.accumulatedExecutionNs());
        } catch (Throwable failure) {
            state.clear();
            hooks.failureHandler().accept(failure);
            return new TurnResult(true, false, true, slicesRun, 0L);
        }
    }
}
