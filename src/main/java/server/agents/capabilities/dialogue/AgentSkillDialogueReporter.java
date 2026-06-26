package server.agents.capabilities.dialogue;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AgentSkillDialogueReporter {
    private AgentSkillDialogueReporter() {
    }

    public static Map<Integer, List<AgentSkillReportFlow.SkillLine>> collectLearnedSkillTrees(Character agent) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees = new TreeMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : agent.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            int treeId = skillId / 10000;
            skillTrees.computeIfAbsent(treeId, ignored -> new ArrayList<>())
                    .add(new AgentSkillReportFlow.SkillLine(
                            skillId, skillName(skillId), skillEntry.skillevel));
        }

        for (List<AgentSkillReportFlow.SkillLine> skills : skillTrees.values()) {
            skills.sort(Comparator.comparingInt(AgentSkillReportFlow.SkillLine::id));
        }
        return skillTrees;
    }

    public static List<AgentSkillReportFlow.SkillLine> collectLearnedBeginnerSkills(Character agent) {
        List<AgentSkillReportFlow.SkillLine> beginnerSkills = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : agent.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (!skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            beginnerSkills.add(new AgentSkillReportFlow.SkillLine(
                    skillId, skillName(skillId), skillEntry.skillevel));
        }

        beginnerSkills.sort(Comparator.comparingInt(AgentSkillReportFlow.SkillLine::id));
        return beginnerSkills;
    }

    public static int remainingBeginnerSp(Character agent) {
        int usedBeginnerSp = 0;
        int beginnerSkillBase = agent.getJobType() * 10000000 + 1000;
        for (int i = 0; i < 3; i++) {
            Skill skill = SkillFactory.getSkill(beginnerSkillBase + i);
            if (skill != null) {
                usedBeginnerSp += agent.getSkillLevel(skill);
            }
        }

        return Math.max(0, Math.min(agent.getLevel() - 1, 6) - usedBeginnerSp);
    }

    static String skillName(int skillId) {
        String name = SkillFactory.getSkillName(skillId);
        return name != null && !name.isBlank() ? name : String.valueOf(skillId);
    }
}
