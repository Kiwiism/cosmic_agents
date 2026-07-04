package server.agents.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMessageQueueStateTest {
    @Test
    void ownsQueuedRepliesAndSendingState() {
        AgentMessageQueueState state = new AgentMessageQueueState();

        assertNotNull(state.lock());
        assertTrue(state.isIdle());

        state.setSending(true);
        assertTrue(state.isSending());
        assertFalse(state.isIdle());

        state.setSending(false);
        AgentQueuedMessage message = new AgentQueuedMessage("queued", true);
        state.enqueue(message);

        assertEquals(1, state.size());
        assertSame(message, state.peek());
        assertEquals(List.of(message), state.snapshot());
        assertFalse(state.isIdle());

        assertSame(message, state.poll());
        assertEquals(0, state.size());
        assertNull(state.poll());
        assertTrue(state.isIdle());
    }
}
