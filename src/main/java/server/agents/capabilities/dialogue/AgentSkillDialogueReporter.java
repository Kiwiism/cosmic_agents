package server.agents.capabilities.dialogue;

import client.Character;
import client.Skill;
import constants.game.GameConstants;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AgentSkillDialogueReporter {
    private AgentSkillDialogueReporter() {
    }

    public static Map<Integer, List<AgentSkillReportFlow.SkillLine>> collectLearnedSkillTrees(Character agent) {
        return collectLearnedSkillTrees(agent, AgentSkillGatewayRuntime.skills());
    }

    static Map<Integer, List<AgentSkillReportFlow.SkillLine>> collectLearnedSkillTrees(Character agent,
                                                                                      SkillGateway skillsGateway) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees = new TreeMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : agent.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (agent.isPartnerSessionBorrowedSkill(skillId)
                    || skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            int treeId = skillId / 10000;
            skillTrees.computeIfAbsent(treeId, ignored -> new ArrayList<>())
                    .add(new AgentSkillReportFlow.SkillLine(
                            skillId, skillName(skillId, skillsGateway), skillEntry.skillevel));
        }

        for (List<AgentSkillReportFlow.SkillLine> skills : skillTrees.values()) {
            skills.sort(Comparator.comparingInt(AgentSkillReportFlow.SkillLine::id));
        }
        return skillTrees;
    }

    public static List<AgentSkillReportFlow.SkillLine> collectLearnedBeginnerSkills(Character agent) {
        return collectLearnedBeginnerSkills(agent, AgentSkillGatewayRuntime.skills());
    }

    static List<AgentSkillReportFlow.SkillLine> collectLearnedBeginnerSkills(Character agent,
                                                                            SkillGateway skillsGateway) {
        List<AgentSkillReportFlow.SkillLine> beginnerSkills = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : agent.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (agent.isPartnerSessionBorrowedSkill(skillId)
                    || !skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            beginnerSkills.add(new AgentSkillReportFlow.SkillLine(
                    skillId, skillName(skillId, skillsGateway), skillEntry.skillevel));
        }

        beginnerSkills.sort(Comparator.comparingInt(AgentSkillReportFlow.SkillLine::id));
        return beginnerSkills;
    }

    public static int remainingBeginnerSp(Character agent) {
        return remainingBeginnerSp(agent, AgentSkillGatewayRuntime.skills());
    }

    static int remainingBeginnerSp(Character agent, SkillGateway skillsGateway) {
        int usedBeginnerSp = 0;
        int beginnerSkillBase = agent.getJobType() * 10000000 + 1000;
        for (int i = 0; i < 3; i++) {
            Skill skill = skillsGateway.getSkill(beginnerSkillBase + i);
            if (skill != null) {
                usedBeginnerSp += agent.getSkillLevel(skill);
            }
        }

        return Math.max(0, Math.min(agent.getLevel() - 1, 6) - usedBeginnerSp);
    }

    static String skillName(int skillId) {
        return skillName(skillId, AgentSkillGatewayRuntime.skills());
    }

    static String skillName(int skillId, SkillGateway skillsGateway) {
        String name = skillsGateway.getSkillName(skillId);
        return name != null && !name.isBlank() ? name : String.valueOf(skillId);
    }
}
