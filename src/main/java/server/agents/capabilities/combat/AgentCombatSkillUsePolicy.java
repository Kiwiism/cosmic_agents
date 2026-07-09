package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;

public final class AgentCombatSkillUsePolicy {
    private AgentCombatSkillUsePolicy() {
    }

    public static boolean canPaySkillCost(Character bot, int skillId, int skillLevel) {
        return canPaySkillCost(bot, skillId, skillLevel, AgentSkillGatewayRuntime.skills());
    }

    static boolean canPaySkillCost(Character bot, int skillId, int skillLevel, SkillGateway skills) {
        Skill skill = skills.getSkill(skillId);
        if (skill == null || skillLevel <= 0) {
            return false;
        }

        return skill.getEffect(skillLevel).canPaySkillCost(bot);
    }
}
