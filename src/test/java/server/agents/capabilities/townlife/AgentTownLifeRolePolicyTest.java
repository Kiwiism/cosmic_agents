package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.personality.AgentPersonalityAssignment;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeRolePolicyTest {
    @Test
    void roleIsStableForItsTemporaryAssignmentWindow() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(77);
        when(agent.getMapId()).thenReturn(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentPersonalityState personality = entry.capabilityStates().require(AgentPersonalityState.STATE_KEY);
        personality.assign(
                new AgentPersonalityAssignment(1, 77, "TownTester", "patient", 1, 9_991L, 0L),
                new AgentPersonalityProfile("patient", 1,
                        new AgentPersonalityProfile.Traits(20, 90, 40, 30, 55, 20, 90)),
                true);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 4, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);

        AgentTownLifeState.Role first = AgentTownLifeRolePolicy.resolve(entry, agent, state, 1_000L);
        long assignedUntil = state.roleUntilMs();
        AgentTownLifeState.Role second = AgentTownLifeRolePolicy.resolve(entry, agent, state, 2_000L);

        assertEquals(first, second);
        assertEquals(assignedUntil, state.roleUntilMs());
        assertTrue(assignedUntil >= 91_000L);
    }
}
