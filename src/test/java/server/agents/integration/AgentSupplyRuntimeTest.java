package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.trade.AgentOfferService;

import server.agents.capabilities.supplies.AgentPotionService;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.runtime.AgentMessageQueueStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.integration.AgentSupplyRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentSupplyRuntimeTest {
    @Test
    void requestUpgradeFallsBackToBestGearWhenNoSupplyRequestIsMade() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class)) {
            potions.when(() -> AgentPotionService.requestLowSuppliesFromOwnerAsk(entry, bot)).thenReturn(false);

            AgentSupplyRuntime.handleRequestUpgradeCommand(entry, bot);

            offers.verify(() -> AgentOfferService.clearPendingOfferForOwnerAsk(entry));
            offers.verify(() -> AgentOfferService.requestBestUpgradeFromOwner(entry, bot));
        }
    }

    @Test
    void potionRequestQueuesNoDonorReply() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMessageQueueStateRuntime.setSending(entry, true);

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class)) {
            potions.when(() -> AgentPotionService.offerPotShareToOwner(entry, true))
                    .thenReturn(AgentPotionService.OwnerPotShareResult.NO_DONOR);

            AgentSupplyRuntime.handleNeedPotionCommand(entry, true);

            assertTrue(AgentMessageQueueStateRuntime.peek(entry).text().contains("hp"));
        }
    }

    @Test
    void potionRequestQueuesReplyThroughReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            potions.when(() -> AgentPotionService.offerPotShareToOwner(entry, true))
                    .thenReturn(AgentPotionService.OwnerPotShareResult.NO_DONOR);

            AgentSupplyRuntime.handleNeedPotionCommand(entry, true);

            replies.verify(() -> AgentReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void ammoRequestQueuesNotNeededReplyForNonBowOwner() {
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        AgentMessageQueueStateRuntime.setSending(entry, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentSupplyRuntime.handleNeedAmmoCommand(entry);

            String reply = AgentMessageQueueStateRuntime.peek(entry).text();
            assertTrue(reply.contains("ammo") || reply.contains("arrows") || reply.contains("bolts"));
        }
    }

    @Test
    void ammoRequestQueuesReplyThroughReplyRuntime() {
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentSupplyRuntime.handleNeedAmmoCommand(entry);

            replies.verify(() -> AgentReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void supplyRequestCallbacksScheduleThroughSchedulerRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatSupplyRequestFlow.SupplyRequestCallbacks callbacks = AgentSupplyRuntime.supplyRequestCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            callbacks.requestPotion(true);
            callbacks.requestAnyPotion();
            callbacks.requestAmmo();

            scheduler.verify(
                    () -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(3));
        }
    }
}
