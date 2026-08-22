package server.agents.social.policy;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSocialResponderElectionTest {
    @Test
    void electionIsStableAndSelectsExactlyOneCandidate() {
        Character speaker = character(1);
        List<AgentRuntimeEntry> candidates = List.of(entry(10), entry(11), entry(12));

        AgentRuntimeEntry first = AgentSocialResponderElection.elect(
                speaker, candidates, "hello", value -> value.bot().getId());
        AgentRuntimeEntry repeated = AgentSocialResponderElection.elect(
                speaker, candidates, "hello", value -> value.bot().getId());

        assertNotNull(first);
        assertEquals(first, repeated);
    }

    private static AgentRuntimeEntry entry(int id) {
        return new AgentRuntimeEntry(character(id), null, null);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
