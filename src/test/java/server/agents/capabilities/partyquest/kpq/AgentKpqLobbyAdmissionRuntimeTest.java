package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKpqLobbyAdmissionRuntimeTest {
    @Test
    void acceptsOnlyOneEligibleKpqRunWithAValidPartySize() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getLevel()).thenReturn(25);

        assertTrue(AgentKpqLobbyAdmissionRuntime.blocker(agent, "kpq", 4, 1).isEmpty());
        assertEquals("KPQ requires a party size of three or four",
                AgentKpqLobbyAdmissionRuntime.blocker(agent, "kpq", 2, 1));
        assertEquals("Director KPQ admission currently supports one independently owned run",
                AgentKpqLobbyAdmissionRuntime.blocker(agent, "kpq", 4, 2));
        assertEquals("only the KPQ lobby is currently available",
                AgentKpqLobbyAdmissionRuntime.blocker(agent, "lpq", 4, 1));
    }

    @Test
    void rejectsAgentsOutsideTheKpqLevelRange() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getLevel()).thenReturn(20);

        assertEquals("KPQ requires level 21-30",
                AgentKpqLobbyAdmissionRuntime.blocker(agent, "kpq", 4, 1));
    }
}
