package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeEncounterStateTest {
    @Test
    void retainsTypedParticipantRoleTurnAndExpiryWithoutLiveObjects() {
        AgentTownLifeEncounterState state = new AgentTownLifeEncounterState();
        state.begin("encounter-1", AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING,
                AgentTownLifeEncounterState.Role.RESPONDER,
                AgentTownLifeEncounterState.Phase.INVITED,
                22, 22, "east-town-street", "decision-7", 10_000L);

        AgentTownLifeEncounterState.Snapshot invited = state.snapshot();
        assertTrue(invited.active());
        assertEquals(22, invited.turnOwnerAgentId());
        assertEquals("east-town-street", invited.venueId());

        state.transition(AgentTownLifeEncounterState.Phase.REACTING, 11);
        assertEquals(AgentTownLifeEncounterState.Phase.REACTING, state.snapshot().phase());
        assertEquals(11, state.snapshot().turnOwnerAgentId());
        assertFalse(state.expired(9_999L));
        assertTrue(state.expired(10_000L));
    }
}
