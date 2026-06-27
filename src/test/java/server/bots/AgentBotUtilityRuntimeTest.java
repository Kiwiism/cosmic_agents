package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotUtilityRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotUtilityRuntimeTest {
    @Test
    void sellTrashSchedulesLegacyShopVisit() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<BotShopManager> shops = mockStatic(BotShopManager.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotUtilityRuntime.utilityCallbacks(entry).sellTrash();

            shops.verify(() -> BotShopManager.requestSellTrashVisit(entry, bot));
        }
    }

    @Test
    void makerCommandsScheduleLegacyMakerActions() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotUtilityRuntime.utilityCallbacks(entry).makeCrystals();
            AgentBotUtilityRuntime.utilityCallbacks(entry).disassembleTrash();

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    org.mockito.Mockito.times(2));
        }
    }

    @Test
    void tradeInviteDoesNothingWhenOwnerMissing() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            scheduler.verifyNoInteractions();
        }
    }
}
