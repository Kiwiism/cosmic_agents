package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowTargetRuntimeTest {
    @AfterEach
    void clearRegistry() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void resolvesActiveAgentFromRuntimeRegistry() {
        Character leader = character(1, "Leader", true);
        Character sibling = character(2, "Sibling", true);
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId())
                .add(new AgentRuntimeEntry(sibling, leader, null));

        assertEquals(List.of(leader, sibling), AgentFollowTargetRuntime.followTargetCandidates(leader));
        assertSame(sibling, AgentFollowTargetRuntime.resolveFollowTarget(leader, "sib"));
    }

    private static Character character(int id, String name, boolean loggedInWorld) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.isLoggedinWorld()).thenReturn(loggedInWorld);
        return character;
    }
}
