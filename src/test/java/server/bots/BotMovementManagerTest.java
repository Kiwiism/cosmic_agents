package server.bots;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotMovementManagerTest {
    @Test
    void shouldNotHoldClimbIdleWhileCommittedClimbEdgeIsActive() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.navEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(0, -100),
                0, 0, 10, -100, 40, 100
        );

        assertFalse(BotMovementManager.shouldHoldClimbIdle(entry, 0, 0));
    }

    @Test
    void shouldAllowIdleClimbHoldWithoutCommittedClimbEdge() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(BotMovementManager.shouldHoldClimbIdle(entry, 0, 0));
    }
}
