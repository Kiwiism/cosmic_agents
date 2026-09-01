package server.agents.capabilities.partyquest.opq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** OPQ vocabulary and six-role contract for the shared lobby system. */
public final class AgentOpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "opq", AgentOpqDefinition.RECRUIT_MAP, AgentOpqDefinition.ENTRY_NPC,
            AgentOpqDefinition.MIN_LEVEL, AgentOpqDefinition.MAX_LEVEL,
            AgentOpqDefinition.PARTY_SIZE, 0, 80, phrases(), List.of(
            new AgentPartyQuestLobbyProfile.MemberRequirement("warrior", 1, c -> c.getJob() != null && c.getJob().isA(client.Job.WARRIOR)),
            new AgentPartyQuestLobbyProfile.MemberRequirement("magician", 1, c -> c.getJob() != null && c.getJob().isA(client.Job.MAGICIAN)),
            new AgentPartyQuestLobbyProfile.MemberRequirement("bowman", 1, c -> c.getJob() != null && c.getJob().isA(client.Job.BOWMAN)),
            new AgentPartyQuestLobbyProfile.MemberRequirement("thief", 1, c -> c.getJob() != null && c.getJob().isA(client.Job.THIEF)),
            new AgentPartyQuestLobbyProfile.MemberRequirement("pirate", 1, c -> c.getJob() != null && c.getJob().isA(client.Job.PIRATE))),
            List.of("Looking for an OPQ party.", "Tower of Goddess party forming."),
            900L, 2_400L);

    private AgentOpqLobbyProfile() { }
    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> result = new ArrayList<>();
        for (String name : List.of("opq", "orbis pq", "orbis party quest", "tower of goddess")) {
            result.add(new AgentPartyQuestLobbyProfile.Phrase(AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS, "recruiting for " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS, "lfm " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN, "looking for " + name));
            result.add(new AgentPartyQuestLobbyProfile.Phrase(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN, "invite me " + name));
        }
        return List.copyOf(result);
    }
}
