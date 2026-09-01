package server.agents.capabilities.partyquest.epq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;
import client.Job;

import java.util.ArrayList;
import java.util.List;

/** EPQ-only vocabulary and exact five-family contract for the shared lobby renderer. */
public final class AgentEpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "epq", AgentEpqDefinition.RECRUIT_MAP, AgentEpqDefinition.ENTRY_NPC,
            AgentEpqDefinition.MIN_LEVEL, AgentEpqDefinition.MAX_LEVEL,
            AgentEpqRosterRequirementPolicy.PARTY_SIZE, -100, 100, phrases(),
            requirements(),
            List.of("Looking for an EPQ party.", "Ellin Forest party forming."),
            900L, 2_400L);

    private AgentEpqLobbyProfile() { }
    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }

    private static List<AgentPartyQuestLobbyProfile.MemberRequirement> requirements() {
        return List.of(
                requirement(Job.WARRIOR, "one Warrior"),
                requirement(Job.MAGICIAN, "one Magician"),
                requirement(Job.BOWMAN, "one Bowman"),
                requirement(Job.THIEF, "one Thief"),
                requirement(Job.PIRATE, "one Pirate"));
    }

    private static AgentPartyQuestLobbyProfile.MemberRequirement requirement(
            Job branch, String description) {
        return new AgentPartyQuestLobbyProfile.MemberRequirement(description, 1,
                member -> member.getJob() != null && member.getJob().isA(branch));
    }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> result = new ArrayList<>();
        for (String name : List.of("epq", "ellin pq", "ellin forest pq")) {
            result.add(new AgentPartyQuestLobbyProfile.Phrase(
                    AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS, "recruiting for " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(
                    AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS, "lfm " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(
                    AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN, "looking for " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(
                    AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN, "invite me " + name));
        }
        return List.copyOf(result);
    }
}
