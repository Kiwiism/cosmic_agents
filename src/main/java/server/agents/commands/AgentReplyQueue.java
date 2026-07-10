package server.agents.commands;

import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.AgentBoundedExecutorFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned reply queue primitive. Legacy bot chat code adapts its runtime state into this class
 * until chat handling is fully reconstructed under Agent modules.
 */
public final class AgentReplyQueue {
    private static final long QUEUED_MESSAGE_SPACING_ESTIMATE_MS = 5_200L;
    private static final int MAX_PENDING_MESSAGES = AgentBoundedExecutorFactory.positiveIntegerProperty(
            "agents.async.dialogue.queueCapacity", 32);

    private AgentReplyQueue() {
    }

    public interface State {
        Object lock();

        int size();

        void enqueue(AgentQueuedMessage message);

        boolean contains(AgentQueuedMessage message);

        boolean removeOldestUndirected();

        AgentQueuedMessage poll();

        boolean isSending();

        void setSending(boolean sending);
    }

    public interface Dispatcher {
        void dispatch(AgentQueuedMessage message);

        void scheduleNext(Runnable task, int delayMs);
    }

    public static long queueSay(State state, String message, Dispatcher dispatcher) {
        return queueMessageWithEstimatedDelay(state, message, false, dispatcher);
    }

    public static long queueReply(State state, String message, Dispatcher dispatcher) {
        return queueMessageWithEstimatedDelay(state, message, true, dispatcher);
    }

    public static long queueMessageWithEstimatedDelay(
            State state,
            String message,
            boolean ownerDirected,
            Dispatcher dispatcher) {
        long estimatedDelayMs;
        synchronized (state.lock()) {
            estimatedDelayMs = state.isSending()
                    ? (long) (state.size() + 1) * QUEUED_MESSAGE_SPACING_ESTIMATE_MS
                    : 0L;
            AgentQueuedMessage queuedMessage = new AgentQueuedMessage(message, ownerDirected);
            if (state.size() >= MAX_PENDING_MESSAGES) {
                if (state.contains(queuedMessage)) {
                    AgentAsyncQueueMetrics.recordCoalesced("dialogue", state.size());
                    return estimatedDelayMs;
                }
                if (!ownerDirected || !state.removeOldestUndirected()) {
                    AgentAsyncQueueMetrics.recordRejected("dialogue", state.size());
                    return estimatedDelayMs;
                }
            }
            state.enqueue(queuedMessage);
            AgentAsyncQueueMetrics.recordSubmitted("dialogue", state.size());
            if (!state.isSending()) {
                state.setSending(true);
                drain(state, dispatcher);
            }
        }
        return estimatedDelayMs;
    }

    private static void drain(State state, Dispatcher dispatcher) {
        AgentQueuedMessage message;
        synchronized (state.lock()) {
            message = state.poll();
            AgentAsyncQueueMetrics.recordDepth("dialogue", state.size());
            if (message == null) {
                state.setSending(false);
                return;
            }
        }
        dispatcher.dispatch(message);
        dispatcher.scheduleNext(() -> drain(state, dispatcher), nextDrainDelayMs());
    }

    private static int nextDrainDelayMs() {
        return ThreadLocalRandom.current().nextInt(4_900, 5_101);
    }
}
