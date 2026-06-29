package server.agents.capabilities.combat;

import client.Character;
import constants.skills.Cleric;
import constants.skills.SuperGM;

public final class AgentCombatSupportPolicy {
    private AgentCombatSupportPolicy() {
    }

    public static boolean canUseDragonRoarPlan(Character bot,
                                               int targetCount,
                                               int minimumTargetsWithoutHealer,
                                               boolean hasNearbyHealSkillAlly) {
        if (bot == null) {
            return false;
        }
        int maxHp = bot.getCurrentMaxHp();
        if (maxHp <= 0 || bot.getHp() * 2 <= maxHp) {
            return false;
        }
        return targetCount >= minimumTargetsWithoutHealer || hasNearbyHealSkillAlly;
    }

    public static boolean needsHeal(Character chr, double targetHpRatio) {
        if (chr == null || !chr.isAlive()) {
            return false;
        }
        int maxHp = chr.getCurrentMaxHp();
        if (maxHp <= 0) {
            return false;
        }
        return chr.getHp() < Math.round(maxHp * targetHpRatio);
    }

    public static boolean hasHealSkill(Character chr) {
        return chr != null
                && (chr.getSkillLevel(Cleric.HEAL) > 0 || chr.getSkillLevel(SuperGM.HEAL_PLUS_DISPEL) > 0);
    }
}
