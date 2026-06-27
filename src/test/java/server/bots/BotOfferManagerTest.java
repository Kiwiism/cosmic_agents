package server.bots;

import client.Character;
import client.Job;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotOfferManagerTest {

    @Test
    void crossbowmanRejectsBowOffers() {
        Character recipient = mock(Character.class);
        when(recipient.getJob()).thenReturn(Job.CROSSBOWMAN);
        assertFalse(BotOfferManager.isWeaponOfferCompatible(recipient, WeaponType.BOW));
    }

    @Test
    void crossbowmanAcceptsCrossbowOffers() {
        Character recipient = mock(Character.class);
        when(recipient.getJob()).thenReturn(Job.CROSSBOWMAN);
        assertTrue(BotOfferManager.isWeaponOfferCompatible(recipient, WeaponType.CROSSBOW));
    }

    @Test
    void requestBestUpgradeBusyReplyUsesAgentReplyAdapter() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.setPendingAction("trade");

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            BotOfferManager.requestBestUpgradeFromOwner(entry, bot);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "busy rn, ask me again in a bit"));
        }
    }

    @Test
    void notifyOwnerGainedEquipSkipsWhenOfferOwnerIsIdle() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);
        Item item = new Item(1002000, (short) 1, (short) 1);

        try (MockedStatic<AgentBotOfferRuntime> offers = mockStatic(AgentBotOfferRuntime.class);
             MockedStatic<BotEquipManager> equipment = mockStatic(BotEquipManager.class)) {
            offers.when(() -> AgentBotOfferRuntime.isOwnerIdleForOffer(entry)).thenReturn(true);

            BotOfferManager.notifyOwnerGainedEquip(entry, bot, item);

            equipment.verify(() -> BotEquipManager.findRecommendationForItem(bot, owner, item), never());
        }
    }

    @Test
    void positiveOwnerUpgradeResponseSchedulesAgentReplyAdapter() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(100);
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.pendingLootOfferItem = new Item(1002000, (short) 1, (short) 1);
        entry.pendingLootOfferRecipientId = 100;
        entry.pendingLootOfferExpiresAt = Long.MAX_VALUE;
        entry.pendingLootOfferBotRequesting = true;

        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            assertTrue(BotOfferManager.handlePendingOfferResponse(entry, owner, "yes"));

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), action.capture()));
            action.getValue().run();
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "ty! inv me?"));
        }
    }

    @Test
    void negativeOwnerUpgradeResponseSchedulesAgentReplyAdapter() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(100);
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.pendingLootOfferItem = new Item(1002000, (short) 1, (short) 1);
        entry.pendingLootOfferRecipientId = 100;
        entry.pendingLootOfferExpiresAt = Long.MAX_VALUE;

        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            assertTrue(BotOfferManager.handlePendingOfferResponse(entry, owner, "no"));

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), action.capture()));
            action.getValue().run();
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "ok, keeping it for now"));
        }
    }
}
