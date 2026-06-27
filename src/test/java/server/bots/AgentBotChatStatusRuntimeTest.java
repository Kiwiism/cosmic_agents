package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotChatStatusRuntime;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotChatStatusRuntimeTest {
    @Test
    void markOwnerActiveAdaptsBotEntryState() {
        Character owner = mock(Character.class);
        Point position = new Point(12, 34);
        when(owner.getPosition()).thenReturn(position);
        BotEntry entry = new BotEntry(null, owner, null);
        entry.setOwnerWasAfk(true);

        AgentBotChatStatusRuntime.markOwnerActive(entry);

        assertFalse(entry.ownerWasAfk());
        assertEquals(position, entry.ownerAfkPosition());
        assertTrue(entry.ownerAfkSinceMs() > 0L);
    }

    @Test
    void ownerIdleAndFidgetExpressionDelegateToAgentStatusRuntime() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setOwnerWasAfk(true);

        assertTrue(AgentBotChatStatusRuntime.isOwnerIdle(entry));
        assertTrue(Set.of(2, 3, 5, 6, 7).contains(AgentBotChatStatusRuntime.randomFidgetExpression()));
    }
}
