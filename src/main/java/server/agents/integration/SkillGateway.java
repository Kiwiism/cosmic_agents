package server.agents.integration;

import client.Skill;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
        rationale = "Skill-provider access is read-only after server data initialization.")
public interface SkillGateway {
    Skill getSkill(int skillId);

    String getSkillName(int skillId);

    default int getSkillMaxLevel(int skillId, int fallback) {
        Skill skill = getSkill(skillId);
        return skill != null ? skill.getMaxLevel() : fallback;
    }
}
