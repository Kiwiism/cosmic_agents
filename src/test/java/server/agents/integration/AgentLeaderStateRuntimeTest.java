package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentLeaderStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLeaderStateRuntimeTest {
    @Test
    void storesLiveLeaderReference() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(123);

        AgentLeaderStateRuntime.setLeader(entry, leader);

        assertSame(leader, AgentLeaderStateRuntime.leader(entry));
        assertTrue(AgentLeaderStateRuntime.matchesLeaderId(entry, 123));
        assertFalse(AgentLeaderStateRuntime.matchesLeaderId(entry, 456));
    }
}
