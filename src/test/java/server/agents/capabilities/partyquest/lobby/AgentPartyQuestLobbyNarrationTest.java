package server.agents.capabilities.partyquest.lobby;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPartyQuestLobbyNarrationTest {
    @Test
    void commonRecruitmentStylesLeadWithCurrentCountAndMissingSeats() {
        AgentPartyQuestLobbyProfile profile = profile("kpq", 21, 30, 4, List.of());

        for (int style = 0; style <= 6; style++) {
            String message = AgentPartyQuestLobbyNarration.recruiterMessage(
                    profile, List.of(), 3, style);
            assertTrue(message.contains("3/4"), message);
            assertTrue(message.toLowerCase().contains("1 more"), message);
        }
    }

    @Test
    void compositionStyleReportsCurrentJobs() {
        AgentPartyQuestLobbyProfile profile = profile("kpq", 21, 30, 4, List.of());

        String message = AgentPartyQuestLobbyNarration.recruiterMessage(
                profile, List.of(member(Job.FIGHTER), member(Job.CLERIC), member(Job.ASSASSIN)),
                3, 7L);

        assertTrue(message.contains("fighter"), message);
        assertTrue(message.contains("cleric"), message);
        assertTrue(message.contains("assassin"), message);
        assertTrue(message.contains("3/4"), message);
    }

    @Test
    void kpqRequirementStyleOccasionallyRemindsPlayersOfLevelRange() {
        AgentPartyQuestLobbyProfile profile = profile("kpq", 21, 30, 4, List.of());

        String message = AgentPartyQuestLobbyNarration.recruiterMessage(
                profile, List.of(), 3, 9L);

        assertTrue(message.contains("lv21-30"), message);
        assertTrue(message.contains("3/4"), message);
    }

    @Test
    void activityProfileCanRequestMissingClassSkillRequirements() {
        AgentPartyQuestLobbyProfile profile = profile("lpq", 35, 50, 6, List.of(
                new AgentPartyQuestLobbyProfile.MemberRequirement(
                        "a thief with Dark Sight", 1, member -> member.getJob().isA(Job.THIEF)),
                new AgentPartyQuestLobbyProfile.MemberRequirement(
                        "a magician with Teleport", 1, member -> member.getJob().isA(Job.MAGICIAN))));

        String message = AgentPartyQuestLobbyNarration.recruiterMessage(
                profile, List.of(member(Job.THIEF)), 4, 9L);

        assertFalse(message.contains("Dark Sight"), message);
        assertTrue(message.contains("a magician with Teleport"), message);
        assertTrue(message.contains("4/6"), message);
    }

    private static AgentPartyQuestLobbyProfile profile(
            String key,
            int minimumLevel,
            int maximumLevel,
            int maximumSize,
            List<AgentPartyQuestLobbyProfile.MemberRequirement> requirements) {
        return new AgentPartyQuestLobbyProfile(
                key, 1000, 9000, minimumLevel, maximumLevel, maximumSize,
                -50, 50, List.of(), requirements, List.of());
    }

    private static Character member(Job job) {
        Character member = mock(Character.class);
        when(member.getJob()).thenReturn(job);
        return member;
    }
}
