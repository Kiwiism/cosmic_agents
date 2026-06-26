package server.agents.capabilities.dialogue;

import server.combat.CombatFormulaProvider;

public final class AgentCombatDialogueReporter {
    private AgentCombatDialogueReporter() {
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
