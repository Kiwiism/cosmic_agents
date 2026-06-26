package server.agents.commands;

import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned reply queue primitive. Legacy bot chat code adapts its runtime state into this class
 * until chat handling is fully reconstructed under Agent modules.
 */
public final class AgentReplyQueue {
    private static final long QUEUED_MESSAGE_SPACING_ESTIMATE_MS = 5_200L;

    private AgentReplyQueue() {
    }

    public interface State {
        Deque<AgentQueuedMessage> queue();

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
        synchronized (state.queue()) {
            estimatedDelayMs = state.isSending()
                    ? (long) (state.queue().size() + 1) * QUEUED_MESSAGE_SPACING_ESTIMATE_MS
                    : 0L;
            state.queue().add(new AgentQueuedMessage(message, ownerDirected));
            if (!state.isSending()) {
                state.setSending(true);
                drain(state, dispatcher);
            }
        }
        return estimatedDelayMs;
    }

    private static void drain(State state, Dispatcher dispatcher) {
        AgentQueuedMessage message;
        synchronized (state.queue()) {
            message = state.queue().poll();
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
