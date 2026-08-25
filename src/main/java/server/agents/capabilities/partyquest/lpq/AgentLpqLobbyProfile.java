package server.agents.capabilities.partyquest.lpq;

import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;

import java.util.ArrayList;
import java.util.List;

/** LPQ-specific vocabulary and roster needs consumed by the shared lobby runtime. */
public final class AgentLpqLobbyProfile {
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
                    "LPQ party needs the required skills."));

    private AgentLpqLobbyProfile() {
    }

    public static AgentPartyQuestLobbyProfile profile() { return PROFILE; }

    private static List<AgentPartyQuestLobbyProfile.Phrase> phrases() {
        List<AgentPartyQuestLobbyProfile.Phrase> phrases = new ArrayList<>();
        add(phrases, AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                "recruiting for lpq", "lpq recruiting", "lfm lpq",
                "recruiting for ludi pq", "need members for lpq", "forming lpq");
        add(phrases, AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                "looking for lpq", "looking for ludi pq", "want to join lpq",
                "can i join lpq", "join lpq", "ludi pq", "ludibrium pq");
        return List.copyOf(phrases);
    }

    private static void add(List<AgentPartyQuestLobbyProfile.Phrase> output,
                            AgentPartyQuestLobbyIntent intent, String... substrings) {
        for (String substring : substrings) {
            output.add(new AgentPartyQuestLobbyProfile.Phrase(intent, substring));
        }
    }
}
