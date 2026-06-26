package server.agents.capabilities.dialogue;

import client.Character;
import server.combat.CombatFormulaProvider;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class AgentCombatDialogueReporter {
    public record MobHitProfile(int mobLevel, int mobAvoid) {
    }

    public record ActiveSkillBuffDebugLine(String label, long remainingMs) {
    }

    public record CachedSkillBuffDebugLine(String label, String status) {
    }

    private AgentCombatDialogueReporter() {
    }

    public static String rangeReport(Character agent, boolean magicAttack, MobHitProfile hitProfile) {
        CombatFormulaProvider formulas = CombatFormulaProvider.getInstance();
        int attackStat;
        int accuracy;
        int minDmg;
        int maxDmg;

        if (magicAttack) {
            attackStat = agent.getTotalMagic();
            accuracy = formulas.getTotalMagicAccuracy(agent);
            maxDmg = (int) Math.max(1L, formulas.magicDamageBase(attackStat, agent.getTotalInt()));
            minDmg = (int) Math.max(1L, formulas.magicDamageBaseMin(attackStat, agent.getTotalInt(), 0.1d));
        } else {
            attackStat = agent.getTotalWatk();
            accuracy = formulas.getTotalAccuracy(agent);
            maxDmg = Math.max(1, agent.calculateMaxBaseDamage(attackStat));
            minDmg = Math.max(1, agent.calculateMinBaseDamage(attackStat, formulas.resolvePhysicalMastery(agent)));
        }

        String report = AgentDialogueReportFormatter.range(
                minDmg, maxDmg,
                AgentDialogueReportFormatter.rangeAttackLabel(magicAttack), attackStat,
                AgentDialogueReportFormatter.rangeAccuracyLabel(magicAttack), accuracy);
        if (hitProfile == null) {
            return report;
        }

        double hitChance = magicAttack
                ? formulas.calculateMagicMobHitChance(
                        accuracy, agent.getLevel(), hitProfile.mobLevel(), hitProfile.mobAvoid())
                : formulas.calculatePhysicalMobHitChance(
                        accuracy, agent.getLevel(), hitProfile.mobLevel(), hitProfile.mobAvoid());
        int hitPercent = (int) Math.round(hitChance * 100.0d);
        return AgentDialogueReportFormatter.rangeWithHit(report, hitPercent, hitProfile.mobAvoid());
    }

    public static String critReport(CombatFormulaProvider.CritProfile crit,
                                    CombatFormulaProvider.DamageProfile damage) {
        int critPct = (int) Math.round(crit.critChance() * 100);
        if (critPct == 0) {
            return AgentDialogueCatalog.noCritPassiveReply();
        }

        int critMin = (int) Math.min(99999, Math.floor(damage.minDamage() * crit.critMultiplier()));
        int critMax = (int) Math.min(99999, Math.floor(damage.maxDamage() * crit.critMultiplier()));
        return AgentDialogueReportFormatter.crit(
                critPct, crit.critMultiplier(),
                damage.minDamage(), damage.maxDamage(),
                critMin, critMax);
    }

    public static String debugStatsReport(String route,
                                          int attackSpeed,
                                          double attackCooldownSeconds,
                                          double remainingCooldownSeconds,
                                          int movementTickMs,
                                          int aiTickMs,
                                          String targetName) {
        return String.format(
                "debug: route %s, atk speed %d, atk cd %.2fs, remaining %.2fs, tick %dms, ai %dms, target %s",
                route,
                attackSpeed,
                attackCooldownSeconds,
                remainingCooldownSeconds,
                movementTickMs,
                aiTickMs,
                targetName);
    }

    public static List<String> skillBuffDebugLines(String lastAction,
                                                   long lastActionAgeMs,
                                                   List<ActiveSkillBuffDebugLine> activeBuffs,
                                                   List<CachedSkillBuffDebugLine> cachedBuffs) {
        String formattedLastAction = lastAction;
        if (lastActionAgeMs >= 0) {
            formattedLastAction += " (" + formatBuffAge(lastActionAgeMs) + " ago)";
        }

        return List.of(
                "skill buffs: last: " + formattedLastAction,
                "active: " + activeSkillBuffLine(activeBuffs),
                "cached: " + cachedSkillBuffLine(cachedBuffs)
        );
    }

    public static String skillBuffRebuffStatus(long remainingMs) {
        return "rebuff " + formatBuffAge(remainingMs);
    }

    public static String formatBuffAge(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0) {
            return seconds + "s";
        }
        return minutes + "m" + seconds + "s";
    }

    private static String activeSkillBuffLine(List<ActiveSkillBuffDebugLine> activeBuffs) {
        if (activeBuffs == null || activeBuffs.isEmpty()) {
            return "none";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (ActiveSkillBuffDebugLine buff : activeBuffs) {
            String remaining = buff.remainingMs() > 0
                    ? " " + formatBuffAge(buff.remainingMs()) + " left"
                    : "";
            joiner.add(buff.label() + remaining);
        }
        return joiner.toString().toLowerCase(Locale.ROOT);
    }

    private static String cachedSkillBuffLine(List<CachedSkillBuffDebugLine> cachedBuffs) {
        if (cachedBuffs == null || cachedBuffs.isEmpty()) {
            return "none cached";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (CachedSkillBuffDebugLine buff : cachedBuffs) {
            joiner.add(buff.label() + " (" + buff.status() + ")");
        }
        return joiner.toString().toLowerCase(Locale.ROOT);
    }
}
