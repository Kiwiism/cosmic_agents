package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSupplyReportRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotSupplyReportRuntimeTest {
    @Test
    void potionReportUsesLegacyPotionCountDataAndAgentFormatting() {
        Character bot = mock(Character.class);

        try (MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class)) {
            potions.when(() -> BotPotionManager.countPotions(bot)).thenReturn(new int[]{2, 3});

            assertEquals("I have 2 hp pots and 3 mp pots", AgentBotSupplyReportRuntime.potionReport(bot));
        }
    }

    @Test
    void autopotDebugReportDelegatesToLegacyAutopotDataSource() {
        Character bot = mock(Character.class);

        try (MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class)) {
            potions.when(() -> BotPotionManager.autopotDebugReport(bot)).thenReturn("pots: debug");

            assertEquals("pots: debug", AgentBotSupplyReportRuntime.autopotDebugReport(bot));
        }
    }
}
