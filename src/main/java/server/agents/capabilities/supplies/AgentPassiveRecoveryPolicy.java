package server.agents.capabilities.supplies;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Magician;
import constants.skills.Warrior;
import constants.skills.WhiteKnight;

public final class AgentPassiveRecoveryPolicy {
    private AgentPassiveRecoveryPolicy() {
    }

    public static int hpRecovery(Character agent, int baseRecovery, boolean standingStill) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery + flatHpRecoveryBonus(agent, Warrior.IMPROVED_HPREC);
    }

    public static int mpRecovery(Character agent, int baseRecovery, boolean standingStill) {
        if (!standingStill) {
            return baseRecovery;
        }
        return baseRecovery
                + flatMpRecoveryBonus(agent, Crusader.IMPROVING_MPREC)
                + flatMpRecoveryBonus(agent, WhiteKnight.IMPROVING_MP_RECOVERY)
                + flatMpRecoveryBonus(agent, DawnWarrior.INCREASED_MP_RECOVERY)
                + magicianMpRecoveryBonus(agent);
    }

    static int flatHpRecoveryBonus(Character agent, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = agent.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getHp();
    }

    static int flatMpRecoveryBonus(Character agent, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null) {
            return 0;
        }
        int level = agent.getSkillLevel(skill);
        if (level <= 0) {
            return 0;
        }
        return skill.getEffect(level).getMp();
    }

    static int magicianMpRecoveryBonus(Character agent) {
        Skill skill = SkillFactory.getSkill(Magician.IMPROVED_MP_RECOVERY);
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
