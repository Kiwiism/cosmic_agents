package server.agents.runtime;

import server.agents.capabilities.follow.AgentFollowTargetCandidateService;

import client.Character;
import net.server.world.Party;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowTargetCandidateServiceTest {
    @Test
    void keepsLegacyCandidateOrderAndFiltersInvalidEntries() {
        Character leader = character(1, "Leader", true);
        Character partyMember = character(2, "Party", true);
        Character offlinePartyMember = character(3, "OfflineParty", false);
        Character siblingAgent = character(4, "Sibling", true);
        Character offlineSiblingAgent = character(5, "OfflineSibling", false);

        when(leader.getParty()).thenReturn(mock(Party.class));
        when(leader.getPartyMembersOnline()).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList(
                leader,
                null,
                partyMember,
                offlinePartyMember)));

        List<Character> candidates = AgentFollowTargetCandidateService.candidates(
                leader,
                new AgentFollowTargetCandidateService.Hooks(
                        leaderCharId -> List.of(
                                new AgentRuntimeEntry(partyMember, leader, null),
                                new AgentRuntimeEntry(offlineSiblingAgent, leader, null),
                                new AgentRuntimeEntry(siblingAgent, leader, null))));

        assertEquals(List.of(leader, partyMember, siblingAgent), candidates);
    }

    @Test
    void omitsOfflineLeaderButStillIncludesValidSiblings() {
        Character leader = character(1, "Leader", false);
        Character siblingAgent = character(4, "Sibling", true);

        List<Character> candidates = AgentFollowTargetCandidateService.candidates(
                leader,
                new AgentFollowTargetCandidateService.Hooks(
                        leaderCharId -> List.of(new AgentRuntimeEntry(siblingAgent, leader, null))));

        assertEquals(List.of(siblingAgent), candidates);
    }

    private static Character character(int id, String name, boolean loggedInWorld) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.isLoggedinWorld()).thenReturn(loggedInWorld);
        return character;
    }
}
