package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentQueuedMessage;
import server.agents.integration.AgentBotMessageQueueStateRuntime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMessageQueueStateRuntimeTest {
    @Test
    void AdaptsAgentRuntimeEntryMessageQueueAndSendingState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentBotMessageQueueStateRuntime.isIdle(entry));

        AgentBotMessageQueueStateRuntime.setSending(entry, true);
        assertTrue(AgentBotMessageQueueStateRuntime.isSending(entry));
        assertFalse(AgentBotMessageQueueStateRuntime.isIdle(entry));

        AgentBotMessageQueueStateRuntime.setSending(entry, false);
        AgentQueuedMessage message = new AgentQueuedMessage("queued", true);
        assertNotNull(AgentBotMessageQueueStateRuntime.lock(entry));
        AgentBotMessageQueueStateRuntime.enqueue(entry, message);
        assertEquals(1, AgentBotMessageQueueStateRuntime.size(entry));
        assertSame(message, AgentBotMessageQueueStateRuntime.peek(entry));
        assertEquals(List.of(message), AgentBotMessageQueueStateRuntime.snapshot(entry));
        assertFalse(AgentBotMessageQueueStateRuntime.isIdle(entry));

        assertSame(message, AgentBotMessageQueueStateRuntime.poll(entry));
        assertEquals(0, AgentBotMessageQueueStateRuntime.size(entry));
        assertNull(AgentBotMessageQueueStateRuntime.poll(entry));
    }
}
