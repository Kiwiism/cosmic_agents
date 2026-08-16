package server.agents.context;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentContextRuntimeTest {
    @Test
    void projectsRelationshipIdentityWithoutExposingMutableCharacters() {
        Character agent = mock(Character.class);
        Character participant = mock(Character.class);
        when(agent.getId()).thenReturn(17);
        when(agent.getName()).thenReturn("ContextAgent");
        when(participant.getId()).thenReturn(23);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, participant, null);
        entry.relationshipState().setCohortId(41L);
        entry.relationshipState().setFormationId(43L);

        AgentContextSnapshot snapshot = AgentContextRuntime.snapshot(entry);

        assertEquals(17, snapshot.characterId());
        assertEquals("ContextAgent", snapshot.characterName());
        assertEquals(23, snapshot.interactionTargetCharacterId());
        assertEquals(41L, snapshot.cohortId());
        assertEquals(43L, snapshot.formationId());
    }
}
