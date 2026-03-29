package server.bots;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BotNavigationManagerTest {
    @Test
    void shouldPromoteFirstActionableEdgePastLeadingZeroDistanceWalks() {
        BotNavigationGraph.Edge collapsed = BotNavigationManager.collapseLeadingWalkEdges(List.of(
                new BotNavigationGraph.Edge(1, 2, BotNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(2, 3, BotNavigationGraph.EdgeType.WALK,
                        new Point(528, -914), new Point(528, -914),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(3, 4, BotNavigationGraph.EdgeType.JUMP,
                        new Point(540, -914), new Point(612, -980),
                        9, 0, 0, 0, 0, 300)
        ));

        assertNotNull(collapsed);
        assertEquals(BotNavigationGraph.EdgeType.JUMP, collapsed.type);
        assertEquals(1, collapsed.fromRegionId);
        assertEquals(4, collapsed.toRegionId);
        assertEquals(new Point(540, -914), collapsed.startPoint);
        assertEquals(new Point(612, -980), collapsed.endPoint);
        assertEquals(400, collapsed.cost);
    }

    @Test
    void shouldDropLeadingWalkChainWhenItConsumesNoMovement() {
        BotNavigationGraph.Edge collapsed = BotNavigationManager.collapseLeadingWalkEdges(List.of(
                new BotNavigationGraph.Edge(181, 184, BotNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50),
                new BotNavigationGraph.Edge(184, 190, BotNavigationGraph.EdgeType.WALK,
                        new Point(565, -2135), new Point(565, -2135),
                        0, 0, 0, 0, 0, 50)
        ));

        assertNull(collapsed);
    }
}
