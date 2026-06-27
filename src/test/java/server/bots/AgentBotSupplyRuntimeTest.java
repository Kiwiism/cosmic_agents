package server.bots;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSupplyRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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
}
