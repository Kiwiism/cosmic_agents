package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillFactory;

public final class AgentCombatSkillUsePolicy {
    private AgentCombatSkillUsePolicy() {
    }

    public static boolean canPaySkillCost(Character bot, int skillId, int skillLevel) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null || skillLevel <= 0) {
            return false;
        }

        return skill.getEffect(skillLevel).canPaySkillCost(bot);
    }
}
