package server.agents.capabilities.partyquest.lpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** LPQ-specific vocabulary and roster needs consumed by the shared lobby runtime. */
public final class AgentLpqLobbyProfile {
    private static final long INVITE_RESPONSE_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqLobbyProfile.INVITE_RESPONSE_MINIMUM_MS");
    private static final long INVITE_RESPONSE_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqLobbyProfile.INVITE_RESPONSE_MAXIMUM_MS");
    private static final AgentPartyQuestLobbyProfile PROFILE = new AgentPartyQuestLobbyProfile(
            "lpq", AgentLpqDefinition.RECRUIT_MAP, AgentLpqDefinition.ENTRY_NPC,
            35, 50, 6, -160, 65, phrases(), List.of(
                    new AgentPartyQuestLobbyProfile.MemberRequirement(
                            "Teleport mage", 1, AgentLpqRosterRequirementPolicy::teleportMagic),
                    new AgentPartyQuestLobbyProfile.MemberRequirement(
                            "Dark Sight", 1, AgentLpqRosterRequirementPolicy::darkSight),
                    new AgentPartyQuestLobbyProfile.MemberRequirement(
                            "ranged attacker", 1, AgentLpqRosterRequirementPolicy::rangedAttack),
                    new AgentPartyQuestLobbyProfile.MemberRequirement(
                            "physical attacker", 1, AgentLpqRosterRequirementPolicy::physicalAttack)),
            List.of("Looking for an LPQ party.", "I want to join Ludi PQ.",
                    "LPQ party needs the required skills."),
            INVITE_RESPONSE_MINIMUM_MS, INVITE_RESPONSE_MAXIMUM_MS);

    private AgentLpqLobbyProfile() {
    }

    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }

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
        return List.copyOf(phrases);
    }

    private static List<String> activityNames() {
        return List.of("lpq", "pq", "ludi pq", "ludi party quest",
                "ludibrium pq", "ludibrium party quest", "tower pq");
    }

    private static void add(List<AgentPartyQuestLobbyProfile.Phrase> output,
                            AgentPartyQuestLobbyIntent intent, String... substrings) {
        for (String substring : substrings) {
            output.add(new AgentPartyQuestLobbyProfile.Phrase(intent, substring));
        }
    }
}
