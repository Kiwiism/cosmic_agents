package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotLeaderStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotLeaderStateRuntimeTest {
    @Test
    void storesLiveLeaderReference() {
        BotEntry entry = new BotEntry(null, null, null);
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(123);

        AgentBotLeaderStateRuntime.setLeader(entry, leader);

        assertSame(leader, AgentBotLeaderStateRuntime.leader(entry));
        assertTrue(AgentBotLeaderStateRuntime.matchesLeaderId(entry, 123));
        assertFalse(AgentBotLeaderStateRuntime.matchesLeaderId(entry, 456));
    }
}
