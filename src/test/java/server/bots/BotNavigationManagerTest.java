package server.bots;

import server.maps.Rope;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldOnlySnapZeroStepClimbExitAtRopeTop() {
        Rope rope = new Rope(675, 143, 215, false);
        BotNavigationGraph.Edge topExit = new BotNavigationGraph.Edge(
                49, 45, BotNavigationGraph.EdgeType.CLIMB,
                new Point(675, 143), new Point(675, 141),
                0, 0, 675, 143, 215, 250
        );
        BotNavigationGraph.Edge bottomExit = new BotNavigationGraph.Edge(
                49, 45, BotNavigationGraph.EdgeType.CLIMB,
                new Point(675, 215), new Point(675, 215),
                0, 0, 675, 143, 215, 250
        );

        assertTrue(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 145), topExit));
        assertTrue(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 171), topExit));
        assertFalse(BotNavigationManager.isTopStepOffExit(rope, new Point(675, 215), bottomExit));
    }

    @Test
    void shouldUsePreciseTargetForCommittedWalkRegionHandoffs() {
        BotNavigationGraph.Edge walkHandoff = new BotNavigationGraph.Edge(
                343, 341, BotNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(13, -1170),
                0, 0, 0, 0, 0, 100
        );
        BotNavigationGraph.Edge noMoveWalk = new BotNavigationGraph.Edge(
                343, 342, BotNavigationGraph.EdgeType.WALK,
                new Point(28, -1167), new Point(28, -1167),
                0, 0, 0, 0, 0, 50
        );

        assertTrue(BotNavigationManager.shouldUsePreciseWalkTarget(walkHandoff));
        assertFalse(BotNavigationManager.shouldUsePreciseWalkTarget(noMoveWalk));
    }

    @Test
    void shouldRejectVerticalJumpFallbackWhenBotIsNinePxFromAnchor() {
        // Regression: GASH pathlog 2026-04-01 — bot at x=-906, vertical jump anchor at x=-915 (dx=9,
        // launchStepX=0). Old code used dx<=10 for all jumps; bot was within tolerance, fallback fired,
        // jump executed from wrong X, landed back on same platform, re-executed forever.
        BotNavigationGraph.Edge verticalJump = new BotNavigationGraph.Edge(
                284, 276, BotNavigationGraph.EdgeType.JUMP,
                new Point(-915, 486), new Point(-915, 425),
                0, 0, 0, 0, 0, 850
        );

        // dx=9 must be rejected for a vertical jump — bot must walk to anchor first
        assertFalse(BotNavigationManager.isWithinJumpFallbackTolerance(verticalJump, 9, 0));
        // tiny genuine drift (≤3px) is still accepted
        assertTrue(BotNavigationManager.isWithinJumpFallbackTolerance(verticalJump, 3, 0));
        assertTrue(BotNavigationManager.isWithinJumpFallbackTolerance(verticalJump, 0, 0));

        // Horizontal jumps keep the 10px tolerance — stepX carries the bot to the landing foothold
        BotNavigationGraph.Edge horizontalJump = new BotNavigationGraph.Edge(
                21, 20, BotNavigationGraph.EdgeType.JUMP,
                new Point(957, 465), new Point(1021, 405),
                8, 0, 0, 0, 0, 850
        );
        assertTrue(BotNavigationManager.isWithinJumpFallbackTolerance(horizontalJump, 9, 0));
        assertTrue(BotNavigationManager.isWithinJumpFallbackTolerance(horizontalJump, 10, 0));
        assertFalse(BotNavigationManager.isWithinJumpFallbackTolerance(horizontalJump, 11, 0));
    }
}
