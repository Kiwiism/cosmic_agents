package server.agents.integration;

import client.Skill;

public interface SkillGateway {
    Skill getSkill(int skillId);

    String getSkillName(int skillId);
}
