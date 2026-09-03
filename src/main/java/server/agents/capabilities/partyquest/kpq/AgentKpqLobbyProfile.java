package server.agents.capabilities.partyquest.kpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** KPQ vocabulary and lobby geometry; behavior remains owned by the generic lobby runtime. */
public final class AgentKpqLobbyProfile {
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "kpq",
            AgentKpqDefinition.RECRUIT_MAP,
            AgentKpqDefinition.ENTRY_NPC,
            21,
            30,
            AgentKpqRecruitmentPolicy.MAX_PARTY_SIZE,
            -180,
            60,
            phrases(),
            List.of(),
            List.of(
                    "Looking for a KPQ party.",
                    "I want to join Kerning PQ.",
                    "Anyone recruiting for KPQ?"));

    private AgentKpqLobbyProfile() {
    }

    public static AgentPartyQuestLobbyProfile profile() {
        return PROFILE;
    }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> phrases = new ArrayList<>();
        add(phrases, AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                "recruiting for kpq", "recruiting kpq", "kpq recruiting",
                "recruiting for pq", "recruiting pq", "pq recruiting",
                "recruiting for kerning pq", "recruiting kerning pq",
                "lfm kpq", "lfm pq", "lfm kerning pq",
                "looking for kpq members", "looking for pq members",
                "looking for members kpq", "looking for members pq",
                "looking for kerning pq members", "need members for kpq",
                "need people for kpq", "kpq need members", "kpq need one", "kpq need 1",
                "need one for kpq", "need 1 for kpq", "need one for pq", "need 1 for pq",
                "forming kpq", "forming pq");
        add(phrases, AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                "looking for kpq", "looking for pq", "looking for kerning pq",
                "looking to join kpq", "looking to join pq", "want to join kpq", "want to join pq",
                "can i join kpq", "can i join pq",
                "im joining", "i'm joining", "i am joining",
                "lf kpq", "lf pq", "join kpq", "join pq", "joining kpq", "joining pq",
                "kerning pq", "kerning party quest", "any kpq", "anyone doing kpq",
                "kpq anyone", "doing kpq", "kpq run", "need kpq", "need a kpq",
                "kpq party please", "kpq pls");
        return List.copyOf(phrases);
    }

    private static void add(List<AgentPartyQuestLobbyProfile.Phrase> output,
                            AgentPartyQuestLobbyIntent intent,
                            String... substrings) {
        for (String substring : substrings) {
            output.add(new AgentPartyQuestLobbyProfile.Phrase(intent, substring));
        }
    }
}
