package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentShopStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentShopStateRuntimeTest {
    @Test
    void adaptsShopTransitionFlagsAndDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(0, AgentShopStateRuntime.shopApproachDelayMs(entry));

        AgentShopStateRuntime.startShopVisit(entry, null, null, 250, 1_000L);
        AgentShopStateRuntime.markShopSequenceActive(entry, 2_000L);

        assertTrue(AgentShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentShopStateRuntime.shopSequenceActive(entry));
        assertTrue(AgentShopStateRuntime.hasActiveShopTransition(entry));
        assertEquals(250, AgentShopStateRuntime.shopApproachDelayMs(entry));
    }

    @Test
    void adaptsShopTargetsWithFallbackAndPointCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertNull(AgentShopStateRuntime.activeShopTargetPosition(entry));

        AgentShopStateRuntime.startShopVisit(entry, new Point(100, 200), null, 0, 1_000L);

        Point npcPosition = AgentShopStateRuntime.shopNpcPosition(entry);
        npcPosition.x = 999;

        assertEquals(new Point(100, 200), AgentShopStateRuntime.shopNpcPosition(entry));
        assertEquals(new Point(100, 200), AgentShopStateRuntime.activeShopTargetPosition(entry));

        AgentShopStateRuntime.startShopVisit(entry, new Point(100, 200), new Point(300, 400), 0, 1_000L);

        Point targetPosition = AgentShopStateRuntime.shopTargetPosition(entry);
        targetPosition.y = 888;

        assertEquals(new Point(300, 400), AgentShopStateRuntime.shopTargetPosition(entry));
        assertEquals(new Point(300, 400), AgentShopStateRuntime.activeShopTargetPosition(entry));
    }

    @Test
    void adaptsShopVisitLifecycleAndClearing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentShopStateRuntime.startShopVisit(
                entry,
                new Point(10, 20),
                new Point(30, 40),
                350,
                1_000L);
        AgentShopStateRuntime.setShopSellTrashPending(entry, true);

        assertTrue(AgentShopStateRuntime.shopVisitPending(entry));
        assertTrue(AgentShopStateRuntime.shopSellTrashPending(entry));
        assertEquals(new Point(10, 20), AgentShopStateRuntime.shopNpcPosition(entry));
        assertEquals(new Point(30, 40), AgentShopStateRuntime.shopTargetPosition(entry));
        assertEquals(350, AgentShopStateRuntime.shopApproachDelayMs(entry));
        assertTrue(AgentShopStateRuntime.visitTimedOut(entry, 31_001L, 30_000L));

        AgentShopStateRuntime.markShopSequenceActive(entry, 2_000L);

        assertTrue(AgentShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentShopStateRuntime.visitTimedOut(entry, 40_000L, 30_000L));
        assertTrue(AgentShopStateRuntime.sequenceTimedOut(entry, 47_001L, 45_000L));

        AgentShopStateRuntime.clearShopState(entry);

        assertFalse(AgentShopStateRuntime.shopVisitPending(entry));
        assertFalse(AgentShopStateRuntime.shopSequenceActive(entry));
        assertFalse(AgentShopStateRuntime.shopSellTrashPending(entry));
        assertNull(AgentShopStateRuntime.shopNpcPosition(entry));
        assertNull(AgentShopStateRuntime.shopTargetPosition(entry));
        assertEquals(0, AgentShopStateRuntime.shopApproachDelayMs(entry));
    }

    @Test
    void adaptsShopStuckFallbackAndSequenceValidation() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point npc = new Point(100, 100);
        Point approach = new Point(125, 100);

        AgentShopStateRuntime.startShopVisit(entry, npc, approach, 0, 1_000L);

        assertFalse(AgentShopStateRuntime.stuckNearNpc(entry, new Point(110, 100), 2_000L, 1_000L, 2, 100));
        assertFalse(AgentShopStateRuntime.stuckNearNpc(entry, new Point(111, 100), 2_500L, 1_000L, 2, 100));
        assertTrue(AgentShopStateRuntime.stuckNearNpc(entry, new Point(111, 100), 3_100L, 1_000L, 2, 100));

        assertFalse(AgentShopStateRuntime.sequenceValid(entry, new Point(125, 100), npc, 100));

        AgentShopStateRuntime.markShopSequenceActive(entry, 3_200L);

        assertTrue(AgentShopStateRuntime.sequenceValid(entry, new Point(125, 100), npc, 100));
        assertFalse(AgentShopStateRuntime.sequenceValid(entry, new Point(400, 400), npc, 100));
    }
}
