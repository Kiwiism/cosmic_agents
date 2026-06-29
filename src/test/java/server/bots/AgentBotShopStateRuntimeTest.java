package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotShopStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotShopStateRuntimeTest {
    @Test
    void adaptsShopTransitionFlagsAndDelay() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentBotShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(0, AgentBotShopStateRuntime.shopApproachDelayMs(entry));

        entry.shopVisitPending = true;
        entry.shopSequenceActive = true;
        entry.shopApproachDelayMs = 250;

        assertTrue(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertTrue(AgentBotShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(250, AgentBotShopStateRuntime.shopApproachDelayMs(entry));
    }

    @Test
    void adaptsShopTargetsWithFallbackAndPointCopies() {
        BotEntry entry = new BotEntry(null, null, null);

        assertNull(AgentBotShopStateRuntime.activeShopTargetPosition(entry));

        entry.shopNpcPos = new Point(100, 200);

        Point npcPosition = AgentBotShopStateRuntime.shopNpcPosition(entry);
        npcPosition.x = 999;

        assertEquals(new Point(100, 200), AgentBotShopStateRuntime.shopNpcPosition(entry));
        assertEquals(new Point(100, 200), AgentBotShopStateRuntime.activeShopTargetPosition(entry));

        entry.shopTargetPos = new Point(300, 400);

        Point targetPosition = AgentBotShopStateRuntime.shopTargetPosition(entry);
        targetPosition.y = 888;

        assertEquals(new Point(300, 400), AgentBotShopStateRuntime.shopTargetPosition(entry));
        assertEquals(new Point(300, 400), AgentBotShopStateRuntime.activeShopTargetPosition(entry));
    }

    @Test
    void adaptsShopVisitLifecycleAndClearing() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotShopStateRuntime.startShopVisit(
                entry,
                new Point(10, 20),
                new Point(30, 40),
                350,
                1_000L);
        AgentBotShopStateRuntime.setShopSellTrashPending(entry, true);

        assertTrue(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentBotShopStateRuntime.shopSellTrashPending(entry));
        assertEquals(new Point(10, 20), AgentBotShopStateRuntime.shopNpcPosition(entry));
        assertEquals(new Point(30, 40), AgentBotShopStateRuntime.shopTargetPosition(entry));
        assertEquals(350, AgentBotShopStateRuntime.shopApproachDelayMs(entry));
        assertTrue(AgentBotShopStateRuntime.visitTimedOut(entry, 31_001L, 30_000L));

        AgentBotShopStateRuntime.markShopSequenceActive(entry, 2_000L);

        assertTrue(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentBotShopStateRuntime.visitTimedOut(entry, 40_000L, 30_000L));
        assertTrue(AgentBotShopStateRuntime.sequenceTimedOut(entry, 47_001L, 45_000L));

        AgentBotShopStateRuntime.clearShopState(entry);

        assertFalse(AgentBotShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentBotShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentBotShopStateRuntime.shopSellTrashPending(entry));
        assertNull(AgentBotShopStateRuntime.shopNpcPosition(entry));
        assertNull(AgentBotShopStateRuntime.shopTargetPosition(entry));
        assertEquals(0, AgentBotShopStateRuntime.shopApproachDelayMs(entry));
    }

    @Test
    void adaptsShopStuckFallbackAndSequenceValidation() {
        BotEntry entry = new BotEntry(null, null, null);
        Point npc = new Point(100, 100);
        Point approach = new Point(125, 100);

        AgentBotShopStateRuntime.startShopVisit(entry, npc, approach, 0, 1_000L);

        assertFalse(AgentBotShopStateRuntime.stuckNearNpc(entry, new Point(110, 100), 2_000L, 1_000L, 2, 100));
        assertFalse(AgentBotShopStateRuntime.stuckNearNpc(entry, new Point(111, 100), 2_500L, 1_000L, 2, 100));
        assertTrue(AgentBotShopStateRuntime.stuckNearNpc(entry, new Point(111, 100), 3_100L, 1_000L, 2, 100));

        assertFalse(AgentBotShopStateRuntime.sequenceValid(entry, new Point(125, 100), npc, 100));

        AgentBotShopStateRuntime.markShopSequenceActive(entry, 3_200L);

        assertTrue(AgentBotShopStateRuntime.sequenceValid(entry, new Point(125, 100), npc, 100));
        assertFalse(AgentBotShopStateRuntime.sequenceValid(entry, new Point(400, 400), npc, 100));
    }
}
