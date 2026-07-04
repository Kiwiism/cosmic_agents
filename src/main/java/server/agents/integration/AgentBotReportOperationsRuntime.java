package server.agents.integration;

import client.Character;
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
                AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot(entry));
            }

            @Override
            public void recommendedGear() {
                AgentBotChatReportRuntime.reportRecommendedGear(entry, bot(entry));
            }

            @Override
            public void skills() {
                AgentBotChatReportRuntime.reportSkills(entry, bot(entry));
            }

            @Override
            public void stats() {
                AgentBotChatReportRuntime.reportStats(entry, bot(entry));
            }

            @Override
            public void movementStats() {
                AgentBotChatReportRuntime.reportMovementStats(entry, bot(entry));
            }

            @Override
            public void range() {
                AgentBotChatReportRuntime.reportRange(entry, bot(entry));
            }

            @Override
            public void build() {
                AgentBotChatReportRuntime.reportBuild(entry, bot(entry));
            }

            @Override
            public void inventory() {
                AgentBotChatReportRuntime.reportInventory(entry, bot(entry));
            }

            @Override
            public void mesos() {
                AgentBotChatReportRuntime.reportMesos(entry, bot(entry));
            }

            @Override
            public void exp() {
                AgentBotChatReportRuntime.reportExp(entry, bot(entry));
            }

            @Override
            public void inventorySlots() {
                AgentBotChatReportRuntime.reportInventorySlots(entry, bot(entry));
            }

            @Override
            public void scrolls() {
                AgentBotChatReportRuntime.reportScrolls(entry, bot(entry));
            }

            @Override
            public void potions() {
                AgentBotChatReportRuntime.reportPotions(entry, bot(entry));
            }

            @Override
            public void debugStats() {
                AgentBotChatReportRuntime.reportDebugStats(entry, bot(entry));
            }

            @Override
            public void critDebug() {
                AgentBotChatReportRuntime.reportCritDebug(entry, bot(entry));
            }

            @Override
            public void potDebug() {
                AgentBotChatReportRuntime.reportPotDebug(entry, bot(entry));
            }
        };
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
