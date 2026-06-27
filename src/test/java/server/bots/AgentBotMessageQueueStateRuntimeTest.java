package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentQueuedMessage;
import server.agents.integration.AgentBotMessageQueueStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMessageQueueStateRuntimeTest {
    @Test
    void adaptsBotEntryMessageQueueAndSendingState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertSame(entry.messageQueue(), AgentBotMessageQueueStateRuntime.queue(entry));
        assertTrue(AgentBotMessageQueueStateRuntime.isIdle(entry));

        AgentBotMessageQueueStateRuntime.setSending(entry, true);
        assertTrue(AgentBotMessageQueueStateRuntime.isSending(entry));
        assertFalse(AgentBotMessageQueueStateRuntime.isIdle(entry));

        AgentBotMessageQueueStateRuntime.setSending(entry, false);
        AgentBotMessageQueueStateRuntime.queue(entry).add(new AgentQueuedMessage("queued", true));
        assertFalse(AgentBotMessageQueueStateRuntime.isIdle(entry));
    }
}
