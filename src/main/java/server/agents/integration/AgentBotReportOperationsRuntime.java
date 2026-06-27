package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for report operation wiring while individual
 * report side effects still live in bot runtime adapters.
 */
public final class AgentBotReportOperationsRuntime {
    private AgentBotReportOperationsRuntime() {
    }

    public static AgentChatReportRuntime.ReportOperations reportOperations(BotEntry entry) {
        return new AgentChatReportRuntime.ReportOperations() {
            @Override
            public void help() {
                AgentBotChatReportRuntime.reportHelp(entry);
            }

            @Override
            public void requestUpgrade() {
                AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot());
            }

            @Override
            public void recommendedGear() {
                AgentBotChatReportRuntime.reportRecommendedGear(entry, entry.bot());
            }

            @Override
            public void skills() {
                AgentBotChatReportRuntime.reportSkills(entry, entry.bot());
            }

            @Override
            public void stats() {
                AgentBotChatReportRuntime.reportStats(entry, entry.bot());
            }

            @Override
            public void movementStats() {
                AgentBotChatReportRuntime.reportMovementStats(entry, entry.bot());
            }

            @Override
            public void range() {
                AgentBotChatReportRuntime.reportRange(entry, entry.bot());
            }

            @Override
            public void build() {
                AgentBotChatReportRuntime.reportBuild(entry, entry.bot());
            }

            @Override
            public void inventory() {
                AgentBotChatReportRuntime.reportInventory(entry, entry.bot());
            }

            @Override
            public void mesos() {
                AgentBotChatReportRuntime.reportMesos(entry, entry.bot());
            }

            @Override
            public void exp() {
                AgentBotChatReportRuntime.reportExp(entry, entry.bot());
            }

            @Override
            public void inventorySlots() {
                AgentBotChatReportRuntime.reportInventorySlots(entry, entry.bot());
            }

            @Override
            public void scrolls() {
                AgentBotChatReportRuntime.reportScrolls(entry, entry.bot());
            }

            @Override
            public void potions() {
                AgentBotChatReportRuntime.reportPotions(entry, entry.bot());
            }

            @Override
            public void debugStats() {
                AgentBotChatReportRuntime.reportDebugStats(entry, entry.bot());
            }

            @Override
            public void critDebug() {
                AgentBotChatReportRuntime.reportCritDebug(entry, entry.bot());
            }

            @Override
            public void potDebug() {
                AgentBotChatReportRuntime.reportPotDebug(entry, entry.bot());
            }
        };
    }
}
