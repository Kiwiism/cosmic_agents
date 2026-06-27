package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPendingTradeStateRuntimeTest {
    @Test
    void adaptsPendingTradeActiveGuard() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertTrue(AgentBotPendingTradeStateRuntime.isIdle(entry));

        entry.pendingTradeCategory = "trash";

        assertTrue(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.isIdle(entry));
    }

    @Test
    void adaptsQueuedTradeRetryState() {
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);
        Runnable first = () -> firstRan.set(true);
        Runnable second = () -> secondRan.set(true);

        AgentBotPendingTradeStateRuntime.queueRetry(entry, first, 10_000);
        AgentBotPendingTradeStateRuntime.queueRetry(entry, second, 5_000);

        assertTrue(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
        assertEquals(10_000, AgentBotPendingTradeStateRuntime.retryDelayMs(entry));

        AgentBotPendingTradeStateRuntime.setRetryDelayMs(entry, 0);
        Runnable retry = AgentBotPendingTradeStateRuntime.takeRetry(entry);

        assertSame(first, retry);
        assertFalse(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
        retry.run();
        assertTrue(firstRan.get());
        assertFalse(secondRan.get());
    }

    @Test
    void adaptsShareBudgetCapState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals((short) 5000, AgentBotPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));

        AgentBotPendingTradeStateRuntime.setShareBudget(entry, 2250);

        assertEquals(2250, AgentBotPendingTradeStateRuntime.shareBudget(entry));
        assertEquals((short) 2250, AgentBotPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));
        assertEquals(0, AgentBotPendingTradeStateRuntime.shareBudget(entry));

        AgentBotPendingTradeStateRuntime.setShareBudget(entry, 1000);
        AgentBotPendingTradeStateRuntime.clearShareBudget(entry);

        assertEquals(0, AgentBotPendingTradeStateRuntime.shareBudget(entry));
    }

    @Test
    void adaptsCategoryMessageState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertNull(AgentBotPendingTradeStateRuntime.categoryMessage(entry));

        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "trading equips");

        assertEquals("trading equips", AgentBotPendingTradeStateRuntime.categoryMessage(entry));
        assertEquals("trading equips", AgentBotPendingTradeStateRuntime.takeCategoryMessage(entry));
        assertNull(AgentBotPendingTradeStateRuntime.categoryMessage(entry));

        AgentBotPendingTradeStateRuntime.setCategoryMessage(entry, "trading ammo");
        AgentBotPendingTradeStateRuntime.clearCategoryMessage(entry);

        assertNull(AgentBotPendingTradeStateRuntime.categoryMessage(entry));
    }

    @Test
    void adaptsRecipientIdState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotPendingTradeStateRuntime.recipientId(entry));

        AgentBotPendingTradeStateRuntime.setRecipientId(entry, 1234);

        assertEquals(1234, AgentBotPendingTradeStateRuntime.recipientId(entry));

        AgentBotPendingTradeStateRuntime.clearRecipientId(entry);

        assertEquals(0, AgentBotPendingTradeStateRuntime.recipientId(entry));
    }

    @Test
    void adaptsInviteAnnouncedState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));

        AgentBotPendingTradeStateRuntime.markInviteAnnounced(entry);

        assertTrue(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));

        AgentBotPendingTradeStateRuntime.clearInviteAnnounced(entry);

        assertFalse(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));
    }

    @Test
    void adaptsTimerState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));

        AgentBotPendingTradeStateRuntime.setTimerMs(entry, 1_000);
        AgentBotPendingTradeStateRuntime.addTimerMs(entry, 250);

        assertEquals(1_250, AgentBotPendingTradeStateRuntime.timerMs(entry));

        AgentBotPendingTradeStateRuntime.tickTimerDown(entry, value -> value - 100);

        assertEquals(1_150, AgentBotPendingTradeStateRuntime.timerMs(entry));

        AgentBotPendingTradeStateRuntime.clearTimer(entry);

        assertEquals(0, AgentBotPendingTradeStateRuntime.timerMs(entry));
    }

    @Test
    void adaptsSingleBatchState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPendingTradeStateRuntime.singleBatch(entry));

        AgentBotPendingTradeStateRuntime.setSingleBatch(entry, true);

        assertTrue(AgentBotPendingTradeStateRuntime.singleBatch(entry));

        AgentBotPendingTradeStateRuntime.clearSingleBatch(entry);

        assertFalse(AgentBotPendingTradeStateRuntime.singleBatch(entry));
    }

    @Test
    void adaptsMesoState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(0, AgentBotPendingTradeStateRuntime.meso(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentBotPendingTradeStateRuntime.setMeso(entry, 12_345);

        assertEquals(12_345, AgentBotPendingTradeStateRuntime.meso(entry));
        assertTrue(AgentBotPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentBotPendingTradeStateRuntime.markMesoAdded(entry);

        assertTrue(AgentBotPendingTradeStateRuntime.mesoAdded(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.hasMesoToAdd(entry));

        AgentBotPendingTradeStateRuntime.clearMeso(entry);
        AgentBotPendingTradeStateRuntime.clearMesoAdded(entry);

        assertEquals(0, AgentBotPendingTradeStateRuntime.meso(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.mesoAdded(entry));
    }
}
