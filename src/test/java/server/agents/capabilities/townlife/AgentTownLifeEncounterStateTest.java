package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void carriesBoundedGroupMembershipWithoutLiveCharacterReferences() {
        AgentTownLifeEncounterState state = new AgentTownLifeEncounterState();
        state.begin("group-1", AgentTownLifeEncounterState.Type.SOCIAL_CHAT,
                AgentTownLifeEncounterState.Role.INITIATOR,
                AgentTownLifeEncounterState.Phase.APPROACHING,
                12, 11, List.of(11, 12, 13, 14),
                "central-benches", "group-decision", 20_000L);

        assertEquals(List.of(11, 12, 13, 14), state.snapshot().participantAgentIds());
    }
}
