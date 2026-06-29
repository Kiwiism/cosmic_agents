package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class AgentCombatHitboxIntersectionTest {
    @Test
    void shouldUseMobBoundsWhenBoundsExist() {
        Rectangle hitBox = new Rectangle(100, 100, 40, 40);

        assertTrue(AgentCombatHitboxIntersection.intersectsMonsterBounds(
                hitBox, new Rectangle(130, 110, 30, 30), new Point(500, 500)));
        assertFalse(AgentCombatHitboxIntersection.intersectsMonsterBounds(
                hitBox, new Rectangle(200, 200, 30, 30), new Point(500, 500)));
    }

    @Test
    void shouldStillAcceptMonsterPositionInsideHitBoxWhenBoundsMiss() {
        Rectangle hitBox = new Rectangle(100, 100, 40, 40);

        assertTrue(AgentCombatHitboxIntersection.intersectsMonsterBounds(
                hitBox, new Rectangle(200, 200, 30, 30), new Point(120, 120)));
    }

    @Test
    void shouldFallbackToMonsterPositionWhenBoundsAreMissing() {
        Rectangle hitBox = new Rectangle(100, 100, 40, 40);

        assertTrue(AgentCombatHitboxIntersection.intersectsMonsterBounds(hitBox, null, new Point(120, 120)));
        assertFalse(AgentCombatHitboxIntersection.intersectsMonsterBounds(hitBox, null, new Point(200, 200)));
    }
}
