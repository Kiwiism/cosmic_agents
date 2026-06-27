package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.integration.AgentBotReportOperationsRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotReportOperationsRuntimeTest {
    @Test
    void reportOperationsDelegateToLegacyReportSideEffects() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentChatReportRuntime.ReportOperations operations =
                AgentBotReportOperationsRuntime.reportOperations(entry);

        try (MockedStatic<BotChatReportRuntime> reports = mockStatic(BotChatReportRuntime.class);
             MockedStatic<BotChatSupplyRuntime> supplies = mockStatic(BotChatSupplyRuntime.class)) {
            operations.help();
            operations.requestUpgrade();
            operations.recommendedGear();
            operations.skills();
            operations.stats();
            operations.movementStats();
            operations.range();
            operations.build();
            operations.inventory();
            operations.mesos();
            operations.exp();
            operations.inventorySlots();
            operations.scrolls();
            operations.potions();
            operations.debugStats();
            operations.critDebug();
            operations.potDebug();

            reports.verify(() -> BotChatReportRuntime.reportHelp(entry));
            supplies.verify(() -> BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportRecommendedGear(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportSkills(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportStats(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportMovementStats(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportRange(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportBuild(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportInventory(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportMesos(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportExp(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportInventorySlots(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportScrolls(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportPotions(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportDebugStats(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportCritDebug(entry, bot));
            reports.verify(() -> BotChatReportRuntime.reportPotDebug(entry, bot));
        }
    }
}
