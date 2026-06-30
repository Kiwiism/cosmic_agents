package server.bots;

import server.agents.capabilities.shop.AgentShopService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotUtilityReplyRuntime;
import server.agents.integration.AgentBotUtilityRuntime;
import server.agents.integration.AgentBotUtilitySchedulerRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotUtilityRuntimeTest {
    @Test
    void sellTrashSchedulesLegacyShopVisit() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotUtilitySchedulerRuntime> scheduler =
                     mockStatic(AgentBotUtilitySchedulerRuntime.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class)) {
            scheduler.when(() -> AgentBotUtilitySchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotUtilityRuntime.utilityCallbacks(entry).sellTrash();

            shops.verify(() -> AgentShopService.requestSellTrashVisit(entry, bot));
        }
    }

    @Test
    void makerCommandsScheduleLegacyMakerActions() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotUtilitySchedulerRuntime> scheduler =
                     mockStatic(AgentBotUtilitySchedulerRuntime.class)) {
            AgentBotUtilityRuntime.utilityCallbacks(entry).makeCrystals();
            AgentBotUtilityRuntime.utilityCallbacks(entry).disassembleTrash();

            scheduler.verify(() -> AgentBotUtilitySchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    org.mockito.Mockito.times(2));
        }
    }

    @Test
    void tradeInviteDoesNothingWhenOwnerMissing() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotUtilitySchedulerRuntime> scheduler =
                     mockStatic(AgentBotUtilitySchedulerRuntime.class)) {
            AgentBotUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void tradeInviteReplyAndTradeInviteScheduleThroughUtilityAdapters() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);

        try (MockedStatic<AgentBotUtilitySchedulerRuntime> scheduler =
                     mockStatic(AgentBotUtilitySchedulerRuntime.class);
             MockedStatic<AgentBotUtilityReplyRuntime> replies = mockStatic(AgentBotUtilityReplyRuntime.class)) {
            scheduler.when(() -> AgentBotUtilitySchedulerRuntime.afterRandomDelay(eq(600), eq(1000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            replies.verify(() -> AgentBotUtilityReplyRuntime.replyNow(eq(entry), any(String.class)));
            scheduler.verify(() -> AgentBotUtilitySchedulerRuntime.afterRandomDelay(eq(800), eq(1200), any(Runnable.class)));
        }
    }

    @Test
    void utilityReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotUtilityReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void utilitySchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotUtilitySchedulerRuntime.afterRandomDelay(500, 700, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 700, action));
        }
    }
}
