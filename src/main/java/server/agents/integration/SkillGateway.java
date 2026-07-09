package server.agents.integration;

import client.Skill;

public interface SkillGateway {
    Skill getSkill(int skillId);

    String getSkillName(int skillId);

    default int getSkillMaxLevel(int skillId, int fallback) {
        Skill skill = getSkill(skillId);
        return skill != null ? skill.getMaxLevel() : fallback;
    }
}
