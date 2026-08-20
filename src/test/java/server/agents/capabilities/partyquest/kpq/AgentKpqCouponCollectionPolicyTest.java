package server.agents.capabilities.partyquest.kpq;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.maps.MapItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKpqCouponCollectionPolicyTest {
    private static final long INTERVAL_MS = 90_000L;
    private static final long MAXIMUM_MS = 30_000L;

    @Test
    void startsRotatingCollectorAtNinetySecondsWhenCouponsRemainOnFloor() {
        AgentKpqCouponCollectionPolicy.Decision before = decide(89_999L, 0L, 0L, 5, 20);
        assertFalse(before.sweepActive());

        AgentKpqCouponCollectionPolicy.Decision due = decide(90_000L, 0L, 0L, 5, 20);
        assertTrue(due.sweepActive());
        assertTrue(due.rotateCollector());
        assertEquals(90_000L, due.sweepStartedAtMs());

        AgentKpqCouponCollectionPolicy.Decision rotate = decide(
                120_000L, due.sweepStartedAtMs(), due.nextSweepAtMs(), 2, 20);
        assertTrue(rotate.sweepActive());
        assertTrue(rotate.rotateCollector());
        assertEquals(120_000L, rotate.sweepStartedAtMs());
    }

    @Test
    void sufficientFloorCouponsDoNotStartMapWideCollectionBeforeNinetySeconds() {
        AgentKpqCouponCollectionPolicy.Decision covered = decide(10_000L, 0L, 0L, 12, 12);
        assertFalse(covered.sweepActive());
        assertEquals(0L, covered.sweepStartedAtMs());
        assertEquals(90_000L, covered.nextSweepAtMs());
    }

    @Test
    void emptyFloorEndsRotationAndSchedulesAnotherNinetySecondCheck() {
        AgentKpqCouponCollectionPolicy.Decision empty = decide(
                100_000L, 90_000L, 90_000L, 0, 12);

        assertFalse(empty.sweepActive());
        assertEquals(0L, empty.sweepStartedAtMs());
        assertEquals(190_000L, empty.nextSweepAtMs());
    }

    @Test
    void groundCountUsesCouponStackQuantityOnce() {
        MapItem stack = couponDrop(3, false);
        MapItem pickedUp = couponDrop(5, true);
        MapItem other = mock(MapItem.class);
        when(other.getItemId()).thenReturn(4_000_000);

        assertEquals(3, AgentKpqCoordinator.couponQuantityOnGround(
                List.of(stack, pickedUp, other)));
    }

    @Test
    void noSweepStartsAfterEveryKnownCouponNeedIsCovered() {
        AgentKpqCouponCollectionPolicy.Decision decision = decide(
                90_000L, 0L, 0L, 5, 0);

        assertFalse(decision.sweepActive());
        assertEquals(0L, decision.sweepStartedAtMs());
    }

    private static AgentKpqCouponCollectionPolicy.Decision decide(
            long nowMs, long sweepStartedAtMs, long nextSweepAtMs,
            int couponsOnGround, int remainingNeed) {
        return AgentKpqCouponCollectionPolicy.decide(
                nowMs, 0L, sweepStartedAtMs, nextSweepAtMs,
                couponsOnGround, remainingNeed, INTERVAL_MS, MAXIMUM_MS);
    }

    private static MapItem couponDrop(int quantity, boolean pickedUp) {
        Item item = mock(Item.class);
        when(item.getQuantity()).thenReturn((short) quantity);
        MapItem drop = mock(MapItem.class);
        when(drop.getItemId()).thenReturn(AgentKpqDefinition.COUPON_ITEM);
        when(drop.getItem()).thenReturn(item);
        when(drop.isPickedUp()).thenReturn(pickedUp);
        return drop;
    }
}
