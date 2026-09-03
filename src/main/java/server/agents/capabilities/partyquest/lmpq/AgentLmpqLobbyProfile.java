package server.agents.capabilities.partyquest.lmpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** LMPQ vocabulary and geometry consumed by the shared lobby runtime. */
public final class AgentLmpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "lmpq", AgentLmpqDefinition.RECRUIT_MAP, AgentLmpqDefinition.ENTRY_NPC,
            AgentLmpqDefinition.MIN_LEVEL, AgentLmpqDefinition.MAX_LEVEL,
            AgentLmpqDefinition.MAX_PARTY_SIZE, -120, 120, phrases(), List.of(),
            List.of("Looking for a Ludibrium Maze party.", "LMPQ party forming."), 900L, 2_400L);

    private AgentLmpqLobbyProfile() { }
    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> result = new ArrayList<>();
        for (String name : List.of("lmpq", "ludi maze", "ludi maze pq",
                "ludibrium maze", "ludibrium maze pq")) {
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
