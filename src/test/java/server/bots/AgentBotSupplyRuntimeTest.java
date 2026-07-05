package server.bots;

import server.agents.capabilities.trade.AgentOfferService;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.integration.AgentBotMessageQueueStateRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSupplyRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentBotSupplyRuntimeTest {
    @Test
    void requestUpgradeFallsBackToBestGearWhenNoSupplyRequestIsMade() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class)) {
            potions.when(() -> AgentPotionService.requestLowSuppliesFromOwnerAsk(entry, bot)).thenReturn(false);

            AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot);

            offers.verify(() -> AgentOfferService.clearPendingOfferForOwnerAsk(entry));
            offers.verify(() -> AgentOfferService.requestBestUpgradeFromOwner(entry, bot));
        }
    }

    @Test
    void potionRequestQueuesNoDonorReply() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMessageQueueStateRuntime.setSending(entry, true);

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class)) {
            potions.when(() -> AgentPotionService.offerPotShareToOwner(entry, true))
                    .thenReturn(AgentPotionService.OwnerPotShareResult.NO_DONOR);

            AgentBotSupplyRuntime.handleNeedPotionCommand(entry, true);

            assertTrue(AgentBotMessageQueueStateRuntime.peek(entry).text().contains("hp"));
        }
    }

    @Test
    void potionRequestQueuesReplyThroughReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            potions.when(() -> AgentPotionService.offerPotShareToOwner(entry, true))
                    .thenReturn(AgentPotionService.OwnerPotShareResult.NO_DONOR);

            AgentBotSupplyRuntime.handleNeedPotionCommand(entry, true);

            replies.verify(() -> AgentBotReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void ammoRequestQueuesNotNeededReplyForNonBowOwner() {
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(null, owner, null);
        AgentBotMessageQueueStateRuntime.setSending(entry, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentBotSupplyRuntime.handleNeedAmmoCommand(entry);

            String reply = AgentBotMessageQueueStateRuntime.peek(entry).text();
            assertTrue(reply.contains("ammo") || reply.contains("arrows") || reply.contains("bolts"));
        }
    }

    @Test
    void ammoRequestQueuesReplyThroughReplyRuntime() {
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(null, owner, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentBotSupplyRuntime.handleNeedAmmoCommand(entry);

            replies.verify(() -> AgentBotReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void supplyRequestCallbacksScheduleThroughSchedulerRuntime() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatSupplyRequestFlow.SupplyRequestCallbacks callbacks = AgentBotSupplyRuntime.supplyRequestCallbacks(entry);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class)) {
            callbacks.requestPotion(true);
            callbacks.requestAnyPotion();
            callbacks.requestAmmo();

            scheduler.verify(
                    () -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(3));
        }
    }
}
