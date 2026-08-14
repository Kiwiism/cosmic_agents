package server.integration;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobHitReactionContextTest {
    @Test
    void rangedDirectionUsesCastFacingInsteadOfTargetPosition() {
        MobHitReactionContext context = MobHitReactionContext.fromAttack(
                125, 3001005, true, false, 0x80,
                new Point(0, 100), new Point(100, 100));

        assertEquals(-1, context.pushDirection());
        assertEquals(MobHitReactionContext.DirectionSource.CAST_FACING,
                context.directionSource());
    }

    @Test
    void meleeDirectionUsesCastTimeOriginWithFacingFallback() {
        MobHitReactionContext right = MobHitReactionContext.fromAttack(
                0, 0, false, false, 0, new Point(0, 100), new Point(20, 100));
        MobHitReactionContext samePositionFacingLeft = MobHitReactionContext.fromAttack(
                0, 0, false, false, 0x80,
                new Point(20, 100), new Point(20, 100));

        assertEquals(1, right.pushDirection());
        assertEquals(-1, samePositionFacingLeft.pushDirection());
    }
}
