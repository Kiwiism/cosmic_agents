package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.integration.AgentBotChatReportRuntime;
import server.agents.integration.AgentBotSupplyRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotReportOperationsRuntimeTest {
    @Test
    void reportOperationsDelegateToLegacyReportSideEffects() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentChatReportRuntime.ReportOperations operations =
                AgentBotChatReportRuntime.reportOperations(entry);

        try (MockedStatic<AgentBotChatReportRuntime> reports = mockStatic(AgentBotChatReportRuntime.class);
             MockedStatic<AgentBotSupplyRuntime> supplies = mockStatic(AgentBotSupplyRuntime.class)) {
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

            reports.verify(() -> AgentBotChatReportRuntime.reportHelp(entry));
            supplies.verify(() -> AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportRecommendedGear(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportSkills(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportStats(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportMovementStats(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportRange(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportBuild(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportInventory(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportMesos(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportExp(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportInventorySlots(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportScrolls(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportPotions(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportDebugStats(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportCritDebug(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportPotDebug(entry, bot));
        }
    }
}
