package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentQueuedMessage;
import server.agents.integration.AgentMessageQueueStateRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMessageQueueStateRuntimeTest {
    @Test
    void AdaptsAgentRuntimeEntryMessageQueueAndSendingState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentMessageQueueStateRuntime.isIdle(entry));

        AgentMessageQueueStateRuntime.setSending(entry, true);
        assertTrue(AgentMessageQueueStateRuntime.isSending(entry));
        assertFalse(AgentMessageQueueStateRuntime.isIdle(entry));

        AgentMessageQueueStateRuntime.setSending(entry, false);
        AgentQueuedMessage message = new AgentQueuedMessage("queued", true);
        assertNotNull(AgentMessageQueueStateRuntime.lock(entry));
        AgentMessageQueueStateRuntime.enqueue(entry, message);
        assertEquals(1, AgentMessageQueueStateRuntime.size(entry));
        assertSame(message, AgentMessageQueueStateRuntime.peek(entry));
        assertEquals(List.of(message), AgentMessageQueueStateRuntime.snapshot(entry));
        assertFalse(AgentMessageQueueStateRuntime.isIdle(entry));

        assertSame(message, AgentMessageQueueStateRuntime.poll(entry));
        assertEquals(0, AgentMessageQueueStateRuntime.size(entry));
        assertNull(AgentMessageQueueStateRuntime.poll(entry));
    }
}
