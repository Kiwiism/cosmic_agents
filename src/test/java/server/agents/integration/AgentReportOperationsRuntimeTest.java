package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSupplyRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentReportOperationsRuntimeTest {
    @Test
    void reportOperationsDelegateToLegacyReportSideEffects() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        server.agents.capabilities.dialogue.AgentChatReportRuntime.ReportOperations operations =
                AgentChatReportRuntime.reportOperations(entry);

        try (MockedStatic<AgentChatReportRuntime> reports = mockStatic(AgentChatReportRuntime.class);
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

            reports.verify(() -> AgentChatReportRuntime.reportHelp(entry));
            supplies.verify(() -> AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportRecommendedGear(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportSkills(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportStats(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportMovementStats(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportRange(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportBuild(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportInventory(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportMesos(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportExp(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportInventorySlots(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportScrolls(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportPotions(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportDebugStats(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportCritDebug(entry, bot));
            reports.verify(() -> AgentChatReportRuntime.reportPotDebug(entry, bot));
        }
    }
}
