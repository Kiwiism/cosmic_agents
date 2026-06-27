package server.bots;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSupplyReplyRuntime;
import server.agents.integration.AgentBotSupplyRuntime;
import server.agents.integration.AgentBotSupplySchedulerRuntime;

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

        try (MockedStatic<BotOfferManager> offers = mockStatic(BotOfferManager.class);
             MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class)) {
            potions.when(() -> BotPotionManager.requestLowSuppliesFromOwnerAsk(entry, bot)).thenReturn(false);

            AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, bot);

            offers.verify(() -> BotOfferManager.clearPendingOfferForOwnerAsk(entry));
            offers.verify(() -> BotOfferManager.requestBestUpgradeFromOwner(entry, bot));
        }
    }

    @Test
    void potionRequestQueuesNoDonorReply() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setMessageSending(true);

        try (MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class)) {
            potions.when(() -> BotPotionManager.offerPotShareToOwner(entry, true))
                    .thenReturn(BotPotionManager.OwnerPotShareResult.NO_DONOR);

            AgentBotSupplyRuntime.handleNeedPotionCommand(entry, true);

            assertTrue(entry.messageQueue().peek().text().contains("hp"));
        }
    }

    @Test
    void potionRequestQueuesReplyThroughSupplyReplyAdapter() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class);
             MockedStatic<AgentBotSupplyReplyRuntime> replies = mockStatic(AgentBotSupplyReplyRuntime.class)) {
            potions.when(() -> BotPotionManager.offerPotShareToOwner(entry, true))
                    .thenReturn(BotPotionManager.OwnerPotShareResult.NO_DONOR);

            AgentBotSupplyRuntime.handleNeedPotionCommand(entry, true);

            replies.verify(() -> AgentBotSupplyReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void ammoRequestQueuesNotNeededReplyForNonBowOwner() {
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(null, owner, null);
        entry.setMessageSending(true);

        try (MockedStatic<BotAttackExecutionProvider> attacks = mockStatic(BotAttackExecutionProvider.class)) {
            attacks.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentBotSupplyRuntime.handleNeedAmmoCommand(entry);

            String reply = entry.messageQueue().peek().text();
            assertTrue(reply.contains("ammo") || reply.contains("arrows") || reply.contains("bolts"));
        }
    }

    @Test
    void ammoRequestQueuesReplyThroughSupplyReplyAdapter() {
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(null, owner, null);

        try (MockedStatic<BotAttackExecutionProvider> attacks = mockStatic(BotAttackExecutionProvider.class);
             MockedStatic<AgentBotSupplyReplyRuntime> replies = mockStatic(AgentBotSupplyReplyRuntime.class)) {
            attacks.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(owner))
                    .thenReturn(WeaponType.SWORD1H);

            AgentBotSupplyRuntime.handleNeedAmmoCommand(entry);

            replies.verify(() -> AgentBotSupplyReplyRuntime.queueReply(eq(entry), any(String.class)));
        }
    }

    @Test
    void supplyRequestCallbacksScheduleThroughSupplySchedulerAdapter() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatSupplyRequestFlow.SupplyRequestCallbacks callbacks = AgentBotSupplyRuntime.supplyRequestCallbacks(entry);

        try (MockedStatic<AgentBotSupplySchedulerRuntime> scheduler =
                     mockStatic(AgentBotSupplySchedulerRuntime.class)) {
            callbacks.requestPotion(true);
            callbacks.requestAnyPotion();
            callbacks.requestAmmo();

            scheduler.verify(
                    () -> AgentBotSupplySchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(3));
        }
    }

    @Test
    void supplyReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotSupplyReplyRuntime.queueReply(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "reply"));
        }
    }

    @Test
    void supplySchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSupplySchedulerRuntime.afterRandomDelay(500, 700, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 700, action));
        }
    }
}
