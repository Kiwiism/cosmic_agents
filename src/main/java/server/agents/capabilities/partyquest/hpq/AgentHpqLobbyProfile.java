package server.agents.capabilities.partyquest.hpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** HPQ-specific vocabulary and lobby geometry for the shared lobby runtime. */
public final class AgentHpqLobbyProfile {
    private static final long INVITE_RESPONSE_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyProfile.INVITE_RESPONSE_MINIMUM_MS");
    private static final long INVITE_RESPONSE_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqLobbyProfile.INVITE_RESPONSE_MAXIMUM_MS");
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "hpq", AgentHpqDefinition.RECRUIT_MAP, AgentHpqDefinition.ENTRY_NPC,
            10, 255, 6, -120, 55, phrases(), List.of(), List.of(
                    "Looking for an HPQ party.",
                    "I want to join Henesys PQ.",
                    "Anyone recruiting for HPQ?"),
            INVITE_RESPONSE_MINIMUM_MS, INVITE_RESPONSE_MAXIMUM_MS);

    private AgentHpqLobbyProfile() {
    }

    public static AgentPartyQuestLobbyProfile profile() {
        return PROFILE;
    }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> phrases = new ArrayList<>();
        for (String activity : activityNames()) {
            add(phrases, AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                    "recruiting for " + activity,
                    activity + " recruiting",
                    "lfm " + activity,
                    "looking for " + activity + " members",
                    "need members for " + activity,
                    "forming " + activity);
            add(phrases, AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                    "looking for " + activity,
                    "looking to join " + activity,
                    "want to join " + activity,
                    "can i join " + activity,
                    "join " + activity,
                    "joining " + activity,
                    "lf " + activity,
                    "invite me " + activity,
                    "invite me for " + activity,
                    activity + " invite me",
                    "anyone doing " + activity);
        }
        add(phrases, AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                "recruiting for pq", "pq recruiting", "lfm pq",
                "looking for pq members", "need members for pq", "forming pq");
        add(phrases, AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                "looking for pq", "looking to join pq", "want to join pq",
                "can i join pq", "join pq", "joining pq", "lf pq",
                "invite me pq", "invite me for pq", "pq invite me",
                "im joining", "i'm joining", "i am joining", "invite me");
        return List.copyOf(phrases);
    }

    private static List<String> activityNames() {
        return List.of(
                "hpq",
                "henesys pq",
                "henesys party quest",
                "hene pq",
                "hene party quest",
                "bunny pq",
                "moon bunny pq",
                "moonbunny pq",
                "rice cake pq");
    }

    private static void add(List<AgentPartyQuestLobbyProfile.Phrase> output,
                            AgentPartyQuestLobbyIntent intent, String... substrings) {
        for (String substring : substrings) {
            output.add(new AgentPartyQuestLobbyProfile.Phrase(intent, substring));
        }
    }
}
