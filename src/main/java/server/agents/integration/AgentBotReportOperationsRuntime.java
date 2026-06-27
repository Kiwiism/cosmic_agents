package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotChatReportRuntime;
import server.bots.BotChatSupplyRuntime;
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
                BotChatReportRuntime.reportHelp(entry);
            }

            @Override
            public void requestUpgrade() {
                BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot());
            }

            @Override
            public void recommendedGear() {
                BotChatReportRuntime.reportRecommendedGear(entry, entry.bot());
            }

            @Override
            public void skills() {
                BotChatReportRuntime.reportSkills(entry, entry.bot());
            }

            @Override
            public void stats() {
                BotChatReportRuntime.reportStats(entry, entry.bot());
            }

            @Override
            public void movementStats() {
                BotChatReportRuntime.reportMovementStats(entry, entry.bot());
            }

            @Override
            public void range() {
                BotChatReportRuntime.reportRange(entry, entry.bot());
            }

            @Override
            public void build() {
                BotChatReportRuntime.reportBuild(entry, entry.bot());
            }

            @Override
            public void inventory() {
                BotChatReportRuntime.reportInventory(entry, entry.bot());
            }

            @Override
            public void mesos() {
                BotChatReportRuntime.reportMesos(entry, entry.bot());
            }

            @Override
            public void exp() {
                BotChatReportRuntime.reportExp(entry, entry.bot());
            }

            @Override
            public void inventorySlots() {
                BotChatReportRuntime.reportInventorySlots(entry, entry.bot());
            }

            @Override
            public void scrolls() {
                BotChatReportRuntime.reportScrolls(entry, entry.bot());
            }

            @Override
            public void potions() {
                BotChatReportRuntime.reportPotions(entry, entry.bot());
            }

            @Override
            public void debugStats() {
                BotChatReportRuntime.reportDebugStats(entry, entry.bot());
            }

            @Override
            public void critDebug() {
                BotChatReportRuntime.reportCritDebug(entry, entry.bot());
            }

            @Override
            public void potDebug() {
                BotChatReportRuntime.reportPotDebug(entry, entry.bot());
            }
        };
    }
}
