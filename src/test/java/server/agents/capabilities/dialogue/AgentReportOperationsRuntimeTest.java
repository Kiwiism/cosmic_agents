package server.agents.capabilities.dialogue;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.supplies.AgentSupplyRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentReportOperationsRuntimeTest {
    @Test
    void reportOperationsDelegateToLegacyReportSideEffects() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentChatReportRuntime.ReportOperations operations =
                AgentChatReportOperationsRuntime.reportOperations(entry);

        try (MockedStatic<AgentChatReportOperationsRuntime> reports = mockStatic(AgentChatReportOperationsRuntime.class);
             MockedStatic<AgentSupplyRuntime> supplies = mockStatic(AgentSupplyRuntime.class)) {
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

            reports.verify(() -> AgentChatReportOperationsRuntime.reportHelp(entry));
            supplies.verify(() -> AgentSupplyRuntime.handleRequestUpgradeCommand(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportRecommendedGear(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportSkills(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportStats(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportMovementStats(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportRange(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportBuild(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportInventory(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportMesos(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportExp(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportInventorySlots(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportScrolls(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportPotions(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportDebugStats(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportCritDebug(entry, bot));
            reports.verify(() -> AgentChatReportOperationsRuntime.reportPotDebug(entry, bot));
        }
    }
}
