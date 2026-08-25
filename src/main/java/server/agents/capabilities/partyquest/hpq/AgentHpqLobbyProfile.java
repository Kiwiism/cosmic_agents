package server.agents.capabilities.partyquest.hpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** HPQ-specific vocabulary and lobby geometry for the shared lobby runtime. */
public final class AgentHpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "hpq", AgentHpqDefinition.RECRUIT_MAP, AgentHpqDefinition.ENTRY_NPC,
            10, 255, 6, -120, 55, phrases(), List.of(), List.of(
                    "Looking for an HPQ party.",
                    "I want to join Henesys PQ.",
                    "Anyone recruiting for HPQ?"));

    private AgentHpqLobbyProfile() {
    }

    public static AgentPartyQuestLobbyProfile profile() {
        return PROFILE;
    }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> phrases = new ArrayList<>();
        add(phrases, AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                "recruiting for hpq", "hpq recruiting", "lfm hpq",
                "recruiting for henesys pq", "need members for hpq", "forming hpq");
        add(phrases, AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                "looking for hpq", "looking for henesys pq", "want to join hpq",
                "can i join hpq", "join hpq", "henesys pq", "anyone doing hpq");
        return List.copyOf(phrases);
    }

    private static void add(List<AgentPartyQuestLobbyProfile.Phrase> output,
                            AgentPartyQuestLobbyIntent intent, String... substrings) {
        for (String substring : substrings) {
            output.add(new AgentPartyQuestLobbyProfile.Phrase(intent, substring));
        }
    }
}
