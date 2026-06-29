package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentMobHitboxProviderTest {
    @Test
    void shouldUseStandZeroBoundsForRightFacingMob() {
        Rectangle bounds = AgentMobHitboxProvider.getInstance().getMobBounds(100100, new Point(100, 200), false);

        assertNotNull(bounds);
        assertEquals(new Rectangle(82, 174, 37, 26), bounds);
    }

    @Test
    void shouldMirrorBoundsForLeftFacingMob() {
        Rectangle bounds = AgentMobHitboxProvider.getInstance().getMobBounds(100100, new Point(100, 200), true);

        assertNotNull(bounds);
        assertEquals(new Rectangle(81, 174, 37, 26), bounds);
    }
}
