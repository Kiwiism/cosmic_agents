package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatReplyRuntimeTest {
    @Test
    void shouldQueueOwnerRepliesAndPublicSayMessages() {
        TestState state = new TestState();
        state.setSending(true);
        TestDispatcher dispatcher = new TestDispatcher();

        long replyDelay = AgentChatReplyRuntime.queueReplyWithEstimatedDelay(state, "owner reply", dispatcher);
        long sayDelay = AgentChatReplyRuntime.queueSayWithEstimatedDelay(state, "party chatter", dispatcher);

        assertEquals(5_200L, replyDelay);
        assertEquals(10_400L, sayDelay);
        AgentQueuedMessage reply = state.queue().poll();
        AgentQueuedMessage say = state.queue().poll();
        assertEquals("owner reply", reply.text());
        assertTrue(reply.ownerDirected());
        assertEquals("party chatter", say.text());
        assertFalse(say.ownerDirected());
    }

    @Test
    void shouldDispatchImmediatelyWhenIdle() {
        TestState state = new TestState();
        TestDispatcher dispatcher = new TestDispatcher();

        long delay = AgentChatReplyRuntime.queueReplyWithEstimatedDelay(state, "hello", dispatcher);

        assertEquals(0L, delay);
        assertEquals("hello", dispatcher.dispatched.text());
        assertTrue(dispatcher.dispatched.ownerDirected());
        assertEquals(1, dispatcher.scheduledCount);
    }

    private static final class TestState implements AgentReplyQueue.State {
        private final Deque<AgentQueuedMessage> queue = new ArrayDeque<>();
        private boolean sending;

        @Override
        public Deque<AgentQueuedMessage> queue() {
            return queue;
        }

        @Override
        public boolean isSending() {
            return sending;
        }

        @Override
        public void setSending(boolean sending) {
            this.sending = sending;
        }
    }

    private static final class TestDispatcher implements AgentReplyQueue.Dispatcher {
        private AgentQueuedMessage dispatched;
        private int scheduledCount;

        @Override
        public void dispatch(AgentQueuedMessage message) {
            dispatched = message;
        }

        @Override
        public void scheduleNext(Runnable task, int delayMs) {
            scheduledCount++;
        }
    }
}
