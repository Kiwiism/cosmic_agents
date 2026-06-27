package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.bots.BotPotionManager;

/**
 * Temporary Agent-owned bridge to legacy bot supply report data.
 */
public final class AgentBotSupplyReportRuntime {
    private AgentBotSupplyReportRuntime() {
    }

    public static String potionReport(Character bot) {
        return AgentSupplyDialogueReporter.potionReport(BotPotionManager.countPotions(bot));
    }

    public static String autopotDebugReport(Character bot) {
        return BotPotionManager.autopotDebugReport(bot);
    }
}
