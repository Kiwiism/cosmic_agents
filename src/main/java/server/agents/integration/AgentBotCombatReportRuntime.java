package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.bots.BotBuffManager;
import server.bots.BotCombatManager;
import server.bots.BotEntry;
import server.combat.CombatFormulaProvider;

import java.util.List;

/**
 * Temporary Agent-owned combat report adapter while combat and buff debug data
 * still comes from bot runtime managers.
 */
public final class AgentBotCombatReportRuntime {
    private AgentBotCombatReportRuntime() {
    }

    public static String debugStatsReport(BotEntry entry, Character bot) {
        return BotCombatManager.describeDebugStats(entry, bot);
    }

    public static String critDebugReport(Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        return AgentCombatDialogueReporter.critReport(crit, dmg);
    }

    public static List<String> buffDebugLines(BotEntry entry, Character bot) {
        return BotBuffManager.getDebugLines(entry, bot);
    }

    public static List<String> skillBuffDebugLines(BotEntry entry, Character bot) {
        return BotCombatManager.getSkillBuffDebugLines(entry, bot);
    }
}
