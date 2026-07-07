package server.bots;

import server.agents.capabilities.shop.AgentShopService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotUtilityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotUtilityRuntimeTest {
    @Test
    void sellTrashSchedulesLegacyShopVisit() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotUtilityRuntime.utilityCallbacks(entry).sellTrash();

            shops.verify(() -> AgentShopService.requestSellTrashVisit((AgentRuntimeEntry) entry, bot));
        }
    }

    @Test
    void makerCommandsScheduleLegacyMakerActions() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class)) {
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

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void tradeInviteReplyAndTradeInviteScheduleThroughReplyAndSchedulerRuntimes() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(600), eq(1000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), any(String.class)));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(800), eq(1200), any(Runnable.class)));
        }
    }
}
