package server.agents.capabilities.partyquest.ppq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** PPQ vocabulary and recruit-map geometry for the shared lobby runtime. */
public final class AgentPpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "ppq", AgentPpqDefinition.RECRUIT_MAP, AgentPpqDefinition.ENTRY_NPC,
            55, 100, AgentPpqDefinition.PARTY_SIZE, -140, 140, phrases(), List.of(),
            List.of("Looking for a Pirate PQ party.", "PPQ party forming."), 900L, 2_400L);
    private AgentPpqLobbyProfile() { }
    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }
    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> result = new ArrayList<>();
        for (String name : List.of("ppq", "pirate pq", "pirate party quest")) {
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
