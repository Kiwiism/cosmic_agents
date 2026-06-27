package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;

/**
 * Temporary Agent-owned bridge for inventory report lines backed by Cosmic
 * character inventory state.
 */
public final class AgentBotInventoryReportRuntime {
    private AgentBotInventoryReportRuntime() {
    }

    public static String inventorySummary(Character bot) {
        return AgentInventoryDialogueReporter.inventorySummary(bot);
    }

    public static String slotsReport(Character bot) {
        return AgentInventoryDialogueReporter.slotsReport(bot);
    }

    public static String scrollReport(Character bot) {
        return AgentInventoryDialogueReporter.scrollReport(bot);
    }
}
