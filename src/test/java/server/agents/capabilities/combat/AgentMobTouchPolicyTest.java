package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class AgentMobTouchPolicyTest {
    @Test
    void shouldBuildStationaryClientStyleFootSweepBounds() {
        assertEquals(new Rectangle(100, 150, 1, 51),
                AgentMobTouchPolicy.botTouchSweepBounds(new Point(100, 200), new Point(100, 200), 50));
    }

    @Test
    void shouldBuildInclusiveSweepAcrossPreviousAndCurrentPositions() {
        assertEquals(new Rectangle(80, 150, 41, 51),
                AgentMobTouchPolicy.botTouchSweepBounds(new Point(80, 200), new Point(120, 200), 50));
    }

    @Test
    void shouldUseCurrentPositionWhenNoPreviousPositionExists() {
        assertEquals(new Rectangle(100, 150, 1, 51),
                AgentMobTouchPolicy.botTouchSweepBounds(null, new Point(100, 200), 50));
    }

    @Test
    void shouldOnlyUseLowerHalfOfMobBoundsForTouchDamage() {
        Rectangle botBounds = new Rectangle(100, 150, 1, 51);

        assertFalse(AgentMobTouchPolicy.lowerHalfIntersects(new Rectangle(95, 60, 20, 80), botBounds));
        assertTrue(AgentMobTouchPolicy.lowerHalfIntersects(new Rectangle(95, 170, 20, 60), botBounds));
    }
}
