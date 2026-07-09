package server.agents.integration.cosmic;

import client.Skill;
import client.SkillFactory;
import server.agents.integration.SkillGateway;

public enum CosmicSkillGateway implements SkillGateway {
    INSTANCE;

    @Override
    public Skill getSkill(int skillId) {
        return SkillFactory.getSkill(skillId);
    }

    @Override
    public String getSkillName(int skillId) {
        return SkillFactory.getSkillName(skillId);
    }
}
