package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentShopStateTest {
    @Test
    void plannedVisitCarriesMandatoryTravelItem() {
        AgentShopState state = new AgentShopState();

        state.startVisit(new java.awt.Point(10, 20), new java.awt.Point(15, 20),
                0, 100, 2_030_005, 1, 1L);

        assertEquals(2_030_005, state.requiredItemId());
        assertEquals(1, state.requiredItemCount());
        state.clear();
        assertEquals(0, state.requiredItemId());
        assertEquals(0, state.requiredItemCount());
    }
    @Test
    void tracksShopVisitLifecycleAndClearing() {
        AgentShopState state = new AgentShopState();

        state.startVisit(new Point(10, 20), new Point(30, 40), 350, 1_000L);
        state.setSellTrashPending(true);

        assertTrue(state.visitPending());
        assertTrue(state.sellTrashPending());
        assertEquals(new Point(10, 20), state.npcPosition());
        assertEquals(new Point(30, 40), state.targetPosition());
        assertEquals(350, state.approachDelayMs());
        assertTrue(state.visitTimedOut(31_001L, 30_000L));

        state.markSequenceActive(2_000L);

        assertTrue(state.sequenceActive());
        assertFalse(state.visitTimedOut(40_000L, 30_000L));
        assertTrue(state.sequenceTimedOut(47_001L, 45_000L));

        state.clear();

        assertFalse(state.visitPending());
        assertFalse(state.sequenceActive());
        assertFalse(state.sellTrashPending());
        assertNull(state.npcPosition());
        assertNull(state.targetPosition());
        assertEquals(0, state.approachDelayMs());
        assertEquals(0, state.minimumMesoReserve());
    }

    @Test
    void returnsDefensivePointCopiesAndFallsBackToNpcTarget() {
        AgentShopState state = new AgentShopState();

        assertNull(state.activeTargetPosition());

        Point npc = new Point(100, 200);
        state.startVisit(npc, null, 0, 1_000L);
        npc.x = 999;
        Point npcPosition = state.npcPosition();
        npcPosition.x = 888;

        assertEquals(new Point(100, 200), state.npcPosition());
        assertEquals(new Point(100, 200), state.activeTargetPosition());
    }

    @Test
    void tracksStuckFallbackAndSequenceValidation() {
        AgentShopState state = new AgentShopState();
        Point npc = new Point(100, 100);
        Point approach = new Point(125, 100);

        state.startVisit(npc, approach, 0, 1_000L);

        assertFalse(state.stuckNearNpc(new Point(110, 100), 2_000L, 1_000L, 2, 100));
        assertFalse(state.stuckNearNpc(new Point(111, 100), 2_500L, 1_000L, 2, 100));
        assertTrue(state.stuckNearNpc(new Point(111, 100), 3_100L, 1_000L, 2, 100));

        assertFalse(state.sequenceValid(new Point(125, 100), npc, 100));

        state.markSequenceActive(3_200L);

        assertTrue(state.sequenceValid(new Point(125, 100), npc, 100));
        assertFalse(state.sequenceValid(new Point(400, 400), npc, 100));
    }

    @Test
    void preservesTheLargestRequiredMesoReserveForAVisit() {
        AgentShopState state = new AgentShopState();

        state.startVisit(new Point(10, 20), null, 0, 1_000, 1_000L);
        state.ensureMinimumMesoReserve(500);
        assertEquals(1_000, state.minimumMesoReserve());

        state.ensureMinimumMesoReserve(1_500);
        assertEquals(1_500, state.minimumMesoReserve());

        state.startVisit(new Point(30, 40), null, 0, 0, 2_000L);
        assertEquals(0, state.minimumMesoReserve());

        state.clear();
        assertEquals(0, state.minimumMesoReserve());
    }
}
