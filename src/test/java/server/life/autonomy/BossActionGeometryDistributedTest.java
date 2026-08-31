package server.life.autonomy;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossActionGeometryDistributedTest {
    @Test
    void onlyPreparedDistributedRegionsHit() {
        BossAction.OrdinaryAttack attack = new BossAction.OrdinaryAttack(
                2, 3, 10, 2_040, 2_280, false,
                new Point(-57, -200), new Point(57, 0),
                -5, 11, 3, true, 280, 0, 0, 0, false);

        Point origin = new Point(0, 0);
        assertTrue(BossActionGeometry.contains(
                attack, origin, new Point(-500, -20), true, List.of(0, 5, 10)));
        assertTrue(BossActionGeometry.contains(
                attack, origin, new Point(0, -20), true, List.of(0, 5, 10)));
        assertTrue(BossActionGeometry.contains(
                attack, origin, new Point(500, -20), true, List.of(0, 5, 10)));
        assertFalse(BossActionGeometry.contains(
                attack, origin, new Point(-300, -20), true, List.of(0, 5, 10)));
    }
}
