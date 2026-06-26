package server.agents.capabilities.dialogue;

import client.Character;
import server.combat.CombatFormulaProvider;

public final class AgentCombatDialogueReporter {
    public record MobHitProfile(int mobLevel, int mobAvoid) {
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
}
