package server.agents.capabilities.combat;

import client.Character;
import constants.skills.Cleric;
import constants.skills.SuperGM;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import server.StatEffect;

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

    public static boolean hasNearbyHealSkillAlly(Character bot, int supportRange, int supportVerticalRange) {
        for (Character member : nearbyPartyMembers(bot, supportRange, supportVerticalRange)) {
            if (hasHealSkill(member)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasNearbyPartyMemberMissingBuff(Character bot,
                                                          StatEffect effect,
                                                          int supportRange,
                                                          int supportVerticalRange) {
        if (effect.getStatups().isEmpty()) {
            return false;
        }

        for (Character target : nearbyPartyMembers(bot, supportRange, supportVerticalRange)) {
            for (var statup : effect.getStatups()) {
                if (target.getBuffedValue(statup.getLeft()) == null) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasPartyMemberInBoundsNeedingHeal(Character bot,
                                                            Rectangle healBounds,
                                                            int supportRange,
                                                            int supportVerticalRange,
                                                            double targetHpRatio) {
        Point botPos = bot.getPosition();
        for (Character target : bot.getPartyMembersOnSameMap()) {
            if (target == null || target.getId() == bot.getId() || !target.isAlive()) {
                continue;
            }
            Point memberPos = target.getPosition();
            if (healBounds != null) {
                if (!healBounds.contains(memberPos)) {
                    continue;
                }
            } else {
                if (Math.abs(memberPos.y - botPos.y) > supportVerticalRange) {
                    continue;
                }
                double rangeSq = (double) supportRange * supportRange;
                if (memberPos.distanceSq(botPos) > rangeSq) {
                    continue;
                }
            }
            if (needsHeal(target, targetHpRatio)) {
                return true;
            }
        }
        return false;
    }

    public static List<Character> nearbyPartyMembers(Character bot, int supportRange, int supportVerticalRange) {
        List<Character> nearby = new ArrayList<>();
        Point botPos = bot.getPosition();
        double rangeSq = (double) supportRange * supportRange;
        for (Character member : bot.getPartyMembersOnSameMap()) {
            if (member == null || member.getId() == bot.getId() || !member.isAlive()) {
                continue;
            }

            Point memberPos = member.getPosition();
            if (Math.abs(memberPos.y - botPos.y) > supportVerticalRange) {
                continue;
            }
            if (memberPos.distanceSq(botPos) > rangeSq) {
                continue;
            }

            nearby.add(member);
        }
        return nearby;
    }
}
