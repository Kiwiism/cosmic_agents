package server.agents.commands;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReplyQueueTest {
    @Test
    void shouldMarkQueuedRepliesAsOwnerDirected() {
        TestState state = new TestState();
        state.setSending(true);

        long replyDelay = AgentReplyQueue.queueReply(state, "owner reply", TestDispatcher.noop());
        long sayDelay = AgentReplyQueue.queueSay(state, "party chatter", TestDispatcher.noop());

        assertEquals(5_200L, replyDelay);
        assertEquals(10_400L, sayDelay);
        AgentQueuedMessage first = state.poll();
        AgentQueuedMessage second = state.poll();
        assertEquals("owner reply", first.text());
        assertTrue(first.ownerDirected());
        assertEquals("party chatter", second.text());
        assertFalse(second.ownerDirected());
    }

    @Test
    void shouldDispatchImmediatelyWhenQueueIsIdle() {
        TestState state = new TestState();
        TestDispatcher dispatcher = new TestDispatcher();

        long delay = AgentReplyQueue.queueReply(state, "hello", dispatcher);

        assertEquals(0L, delay);
        assertEquals(List.of(new AgentQueuedMessage("hello", true)), dispatcher.dispatched);
        assertTrue(state.isSending());
        assertEquals(1, dispatcher.scheduledCount);
    }

    @Test
    void shouldPreserveDuplicatesUntilSaturatedThenCoalesce() {
        TestState state = new TestState();
        state.setSending(true);
        for (int i = 0; i < 31; i++) {
            AgentReplyQueue.queueSay(state, "ambient-" + i, TestDispatcher.noop());
        }
        AgentReplyQueue.queueSay(state, "same", TestDispatcher.noop());
        AgentReplyQueue.queueSay(state, "same", TestDispatcher.noop());

        assertEquals(32, state.size());
    }

    @Test
    void shouldBoundPendingDialogueAndPreferDirectedReplies() {
        TestState state = new TestState();
        state.setSending(true);
        for (int i = 0; i < 40; i++) {
            AgentReplyQueue.queueSay(state, "ambient-" + i, TestDispatcher.noop());
        }

        assertEquals(32, state.size());
        AgentReplyQueue.queueReply(state, "command-result", TestDispatcher.noop());
        assertEquals(32, state.size());
        assertTrue(state.queue.contains(new AgentQueuedMessage("command-result", true)));
    }

    private static final class TestState implements AgentReplyQueue.State {
        private final Deque<AgentQueuedMessage> queue = new ArrayDeque<>();
        private boolean sending;

        @Override
        public Object lock() {
            return queue;
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public void enqueue(AgentQueuedMessage message) {
            queue.add(message);
        }

        @Override
        public boolean contains(AgentQueuedMessage message) {
            return queue.contains(message);
        }

        @Override
        public boolean removeOldestUndirected() {
            var iterator = queue.iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().ownerDirected()) {
                    iterator.remove();
                    return true;
                }
            }
            return false;
        }

        @Override
        public AgentQueuedMessage poll() {
            return queue.poll();
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
        private final List<AgentQueuedMessage> dispatched = new ArrayList<>();
        private int scheduledCount;

        static TestDispatcher noop() {
            return new TestDispatcher();
        }

        @Override
        public void dispatch(AgentQueuedMessage message) {
            dispatched.add(message);
        }

        @Override
        public void scheduleNext(Runnable task, int delayMs) {
            scheduledCount++;
        }
    }
}
