package server.agents.capabilities.dialogue;

import client.Character;
import client.Skill;
import org.junit.jupiter.api.Test;
import server.agents.integration.SkillGateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillDialogueReporterTest {
    @Test
    void shouldCollectLearnedJobSkillTreesLikeLegacyChat() {
        Character agent = mock(Character.class);
        Skill later = new Skill(1101005);
        Skill earlier = new Skill(1100001);
        Skill beginner = new Skill(1000);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(later, new Character.SkillEntry((byte) 20, 0, -1));
        skills.put(earlier, new Character.SkillEntry((byte) 10, 0, -1));
        skills.put(beginner, new Character.SkillEntry((byte) 3, 0, -1));
        when(agent.getSkills()).thenReturn(skills);
        when(agent.isPartnerSessionBorrowedSkill(1101005)).thenReturn(true);
        SkillGateway skillsGateway = mock(SkillGateway.class);
        when(skillsGateway.getSkillName(1101005)).thenReturn("Rage");
        when(skillsGateway.getSkillName(1100001)).thenReturn("Sword Mastery");

        Map<Integer, List<AgentSkillReportFlow.SkillLine>> report =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(agent, skillsGateway);

        assertEquals(List.of(110), List.copyOf(report.keySet()));
        assertEquals(List.of(
                new AgentSkillReportFlow.SkillLine(1100001, "Sword Mastery", 10)),
                report.get(110));
    }

    @Test
    void shouldCollectLearnedBeginnerSkillsLikeLegacyChat() {
        Character agent = mock(Character.class);
        Skill beginner = new Skill(1000);
        Skill job = new Skill(1100001);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(job, new Character.SkillEntry((byte) 10, 0, -1));
        skills.put(beginner, new Character.SkillEntry((byte) 3, 0, -1));
        when(agent.getSkills()).thenReturn(skills);
        SkillGateway skillsGateway = mock(SkillGateway.class);
        when(skillsGateway.getSkillName(1000)).thenReturn("Three Snails");

        assertEquals(List.of(
                new AgentSkillReportFlow.SkillLine(1000, "Three Snails", 3)),
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(agent, skillsGateway));
    }

    @Test
    void shouldCalculateRemainingBeginnerSpLikeLegacyChat() {
        Character agent = mock(Character.class);
        Skill first = new Skill(1000);
        Skill second = new Skill(1001);
        Skill third = new Skill(1002);
        when(agent.getJobType()).thenReturn(0);
        when(agent.getLevel()).thenReturn(7);
        when(agent.getSkillLevel(first)).thenReturn((byte) 1);
        when(agent.getSkillLevel(second)).thenReturn((byte) 2);
        when(agent.getSkillLevel(third)).thenReturn((byte) 0);
        SkillGateway skillsGateway = mock(SkillGateway.class);
        when(skillsGateway.getSkill(1000)).thenReturn(first);
        when(skillsGateway.getSkill(1001)).thenReturn(second);
        when(skillsGateway.getSkill(1002)).thenReturn(third);

        assertEquals(3, AgentSkillDialogueReporter.remainingBeginnerSp(agent, skillsGateway));
    }

    @Test
    void shouldUseSkillIdWhenSkillNameIsMissing() {
        SkillGateway skillsGateway = mock(SkillGateway.class);
        when(skillsGateway.getSkillName(999999)).thenReturn(" ");

        assertEquals("999999", AgentSkillDialogueReporter.skillName(999999, skillsGateway));
    }
}
