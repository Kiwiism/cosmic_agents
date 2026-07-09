package server.agents.capabilities.supplies;

import client.Character;
import client.Skill;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Magician;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;

public final class AgentPassiveRecoveryPolicy {
    private AgentPassiveRecoveryPolicy() {
    }

    public static int hpRecovery(Character agent, int baseRecovery, boolean standingStill) {
        return hpRecovery(agent, baseRecovery, standingStill, AgentSkillGatewayRuntime.skills());
    }

    static int hpRecovery(Character agent, int baseRecovery, boolean standingStill, SkillGateway skills) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery + flatHpRecoveryBonus(agent, Warrior.IMPROVED_HPREC, skills);
    }

    public static int mpRecovery(Character agent, int baseRecovery, boolean standingStill) {
        return mpRecovery(agent, baseRecovery, standingStill, AgentSkillGatewayRuntime.skills());
    }

    static int mpRecovery(Character agent, int baseRecovery, boolean standingStill, SkillGateway skills) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery
                + flatMpRecoveryBonus(agent, Crusader.IMPROVING_MPREC, skills)
                + flatMpRecoveryBonus(agent, WhiteKnight.IMPROVING_MP_RECOVERY, skills)
                + flatMpRecoveryBonus(agent, DawnWarrior.INCREASED_MP_RECOVERY, skills)
                + magicianMpRecoveryBonus(agent, skills);
    }

    static int flatHpRecoveryBonus(Character agent, int skillId, SkillGateway skills) {
        Skill skill = skills.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = agent.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getHp();
    }

    static int flatMpRecoveryBonus(Character agent, int skillId, SkillGateway skills) {
        Skill skill = skills.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = agent.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getMp();
    }

    static int magicianMpRecoveryBonus(Character agent, SkillGateway skills) {
        Skill skill = skills.getSkill(Magician.IMPROVED_MP_RECOVERY);
        if (skill == null) {
            return 0;
        }
        int level = agent.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return Math.max(0, (agent.getInt() / 10) * level);
    }

    static int hpRecoveryFromBonuses(int baseRecovery, boolean standingStill, int improvedHpRecoveryBonus) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery + improvedHpRecoveryBonus;
    }

    static int mpRecoveryFromBonuses(int baseRecovery,
                                     boolean standingStill,
                                     int crusaderBonus,
                                     int whiteKnightBonus,
                                     int dawnWarriorBonus,
                                     int magicianBonus) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery + crusaderBonus + whiteKnightBonus + dawnWarriorBonus + magicianBonus;
    }
}
