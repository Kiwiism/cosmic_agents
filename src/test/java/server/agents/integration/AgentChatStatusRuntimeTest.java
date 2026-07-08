package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentActivityStateRuntime;
import server.agents.integration.AgentChatStatusRuntime;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChatStatusRuntimeTest {
    @Test
    void markOwnerActiveAdaptsAgentRuntimeEntryState() {
        Character owner = mock(Character.class);
        Point position = new Point(12, 34);
        when(owner.getPosition()).thenReturn(position);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        AgentActivityStateRuntime.setOwnerWasAfk(entry, true);

        AgentChatStatusRuntime.markOwnerActive(entry);

        assertFalse(AgentActivityStateRuntime.ownerWasAfk(entry));
        assertEquals(position, AgentActivityStateRuntime.ownerAfkPosition(entry));
        assertTrue(AgentActivityStateRuntime.ownerAfkSinceMs(entry) > 0L);
    }

    @Test
    void ownerIdleAndFidgetExpressionDelegateToAgentStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertTrue(AgentChatStatusRuntime.isOwnerIdle(entry));
        assertTrue(Set.of(2, 3, 5, 6, 7).contains(AgentChatStatusRuntime.randomFidgetExpression()));
    }
}
