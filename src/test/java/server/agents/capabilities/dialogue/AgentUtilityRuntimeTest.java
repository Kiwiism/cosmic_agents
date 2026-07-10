package server.agents.capabilities.dialogue;

import server.agents.capabilities.shop.AgentShopService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentTradeInviteGateway;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentUtilityRuntimeTest {
    @Test
    void sellTrashSchedulesLegacyShopVisit() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentShopService> shops = mockStatic(AgentShopService.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentUtilityRuntime.utilityCallbacks(entry).sellTrash();

            shops.verify(() -> AgentShopService.requestSellTrashVisit(
                    eq((AgentRuntimeEntry) entry), eq(bot), any(InventoryGateway.class)));
        }
    }

    @Test
    void makerCommandsScheduleLegacyMakerActions() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            AgentUtilityRuntime.utilityCallbacks(entry).makeCrystals();
            AgentUtilityRuntime.utilityCallbacks(entry).disassembleTrash();

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(500), eq(700), any(Runnable.class)),
                    org.mockito.Mockito.times(2));
        }
    }

    @Test
    void tradeInviteDoesNothingWhenOwnerMissing() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            AgentUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void tradeInviteReplyAndTradeInviteScheduleThroughReplyAndSchedulerRuntimes() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentTradeInviteGateway> trade = mockStatic(AgentTradeInviteGateway.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(600), eq(1000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), any(String.class)));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(800), eq(1200), any(Runnable.class)));
            trade.verifyNoInteractions();
        }
    }

    @Test
    void tradeInviteGatewayRunsAfterSecondDelay() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentTradeInviteGateway> trade = mockStatic(AgentTradeInviteGateway.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(600), eq(1000), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(entry), eq(800), eq(1200), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(3).run();
                        return null;
                    });

            AgentUtilityRuntime.utilityCallbacks(entry).tradeInvite();

            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), any(String.class)));
            trade.verify(() -> AgentTradeInviteGateway.startAndInvite(bot, owner));
        }
    }
}
