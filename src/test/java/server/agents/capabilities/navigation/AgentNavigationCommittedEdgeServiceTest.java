package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentNavigationCommittedEdgeServiceTest {
    @Test
    void sameEdgeMatchesAllNavigationFields() {
        AgentNavigationGraph.Edge left = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 3, 12, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge same = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 3, 12, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge differentLaunch = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 4, 12, -7, 0, 0, 0, 0);

        assertTrue(AgentNavigationCommittedEdgeService.sameEdge(left, same));
        assertFalse(AgentNavigationCommittedEdgeService.sameEdge(left, differentLaunch));
        assertFalse(AgentNavigationCommittedEdgeService.sameEdge(left, null));
    }

    @Test
    void retainCommittedGroundEdgeOnlyForNonWalkSameRegionReplacement() {
        AgentNavigationGraph.Edge committedDrop = edge(80, 83, AgentNavigationGraph.EdgeType.DROP,
                new Point(7, -34), new Point(-84, 99), 7, 7, 0, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementJump = edge(80, 83, AgentNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95), -35, 45, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementOtherRegion = edge(80, 84, AgentNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95), -35, 45, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementWalk = edge(80, 83, AgentNavigationGraph.EdgeType.WALK,
                new Point(5, -34), new Point(-99, 95), 0, 0, 0, 0, 0, 0, 0);

        assertTrue(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementJump));
        assertFalse(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementOtherRegion));
        assertFalse(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementWalk));
    }

    @Test
    void refreshCommittedGroundEdgeKeepsCurrentEdgeWhenTickIsNotEligible() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacement = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(50, 0), 0, 20, 1, 0, 0, 0, 0);
        AtomicBoolean finderCalled = new AtomicBoolean(false);

        AgentNavigationGraph.Edge result = AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, entry, null, 1, 3, new Point(50, 0), current, false,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> {
                    finderCalled.set(true);
                    return replacement;
                });

        assertSame(current, result);
        assertFalse(finderCalled.get());
    }

    @Test
    void refreshPendingClimbExitEdgeKeepsCurrentEdgeWhenNotEligibleOrExitReady() {
        AgentRuntimeEntry notClimbing = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 10, 100, 0);
        AgentNavigationGraph.Edge replacement = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(50, 0), 0, 20, 1, 0, 0, 0, 0);
        AtomicBoolean finderCalled = new AtomicBoolean(false);

        assertSame(current, AgentNavigationCommittedEdgeService.refreshPendingClimbExitEdge(
                null, notClimbing, null, new Point(0, 0), 1, 3, new Point(50, 0), current, true,
                (graph, bot, botPos, edge) -> false,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> {
                    finderCalled.set(true);
                    return replacement;
                }));
        assertFalse(finderCalled.get());

        AgentRuntimeEntry ready = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(ready, mock(Rope.class));
        assertSame(current, AgentNavigationCommittedEdgeService.refreshPendingClimbExitEdge(
                null, ready, null, new Point(0, 0), 1, 3, new Point(50, 0), current, true,
                (graph, bot, botPos, edge) -> true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> replacement));
    }

    @Test
    void refreshPendingClimbExitEdgeSwitchesWhenExitIsNotReadyAndBetterEdgeExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, mock(Rope.class));
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 10, 100, 0);
        AgentNavigationGraph.Edge replacement = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(50, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, new Point(99, 99));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        AgentNavigationGraph.Edge result = AgentNavigationCommittedEdgeService.refreshPendingClimbExitEdge(
                null, entry, null, new Point(0, 0), 1, 3, new Point(50, 0), current, true,
                (graph, bot, botPos, edge) -> false,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> replacement);

        assertSame(replacement, result);
        assertSame(replacement, AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
        assertEquals(3, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertNull(AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertFalse(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    @Test
    void refreshCommittedGroundEdgeKeepsCurrentEdgeWhileAirborneOrClimbing() {
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacement = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(50, 0), 0, 20, 1, 0, 0, 0, 0);

        AgentRuntimeEntry airborne = new AgentRuntimeEntry(null, null, null);
        AgentMovementStateRuntime.setInAir(airborne, true);
        assertSame(current, AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, airborne, null, 1, 3, new Point(50, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> replacement));

        AgentRuntimeEntry climbing = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(climbing, mock(Rope.class));
        assertSame(current, AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, climbing, null, 1, 3, new Point(50, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> replacement));
    }

    @Test
    void refreshCommittedGroundEdgeKeepsCurrentEdgeWhenReplacementIsMissingSameOrRetained() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(0, 0), new Point(20, 0), 0, 0, 0, 0, 0, 0, 0);
        AgentNavigationGraph.Edge same = edge(1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(0, 0), new Point(20, 0), 0, 0, 0, 0, 0, 0, 0);
        AgentNavigationGraph.Edge retained = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(22, 0), -5, 5, 1, 0, 0, 0, 0);

        assertSame(current, AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, entry, null, 1, 2, new Point(20, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> null));
        assertSame(current, AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, entry, null, 1, 2, new Point(20, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> same));
        assertSame(current, AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, entry, null, 1, 2, new Point(20, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> retained));
    }

    @Test
    void refreshCommittedGroundEdgeSwitchesToBetterReplacementAndUpdatesNavigationState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge current = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(0, 0), new Point(20, 0), 0, 0, 0, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacement = edge(1, 3, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(50, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, new Point(99, 99));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        AgentNavigationGraph.Edge result = AgentNavigationCommittedEdgeService.refreshCommittedGroundEdge(
                null, entry, null, 1, 3, new Point(50, 0), current, true,
                (graph, bot, startRegionId, targetRegionId, targetPos) -> replacement);

        assertSame(replacement, result);
        assertSame(replacement, AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
        assertTrue(AgentNavigationDebugStateRuntime.navTargetRegionId(entry) == 3);
        assertNull(AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertFalse(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    @Test
    void reuseCommittedEdgeUpdatesStoredTargetAndKeepsUsableEdgeFromCurrentRegion() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 2);

        AgentNavigationGraph.Edge reused = AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, entry, 1, 2, (graph, bot, candidate) -> true, (graph, candidate) -> false);

        assertSame(edge, reused);
        assertEquals(2, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
    }

    @Test
    void reuseCommittedEdgeRejectsUnusableOrCompletedEdges() {
        AgentRuntimeEntry unusable = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(unusable, edge);
        assertNull(AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, unusable, 1, 2, (graph, bot, candidate) -> false, (graph, candidate) -> false));

        AgentRuntimeEntry completed = new AgentRuntimeEntry(null, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(completed, edge);
        assertNull(AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, completed, 2, 2, (graph, bot, candidate) -> true, (graph, candidate) -> false));
    }

    @Test
    void reuseCommittedEdgeRejectsStaleRetargetedGroundEdge() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 2);

        assertNull(AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, entry, 1, 3, (graph, bot, candidate) -> true, (graph, candidate) -> false));
        assertEquals(3, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
    }

    @Test
    void reuseCommittedEdgeKeepsClimbAndAirborneArcsUntilTheyCanSettle() {
        AgentNavigationGraph.Edge jump = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 0, 0, 0);
        AgentRuntimeEntry airborne = new AgentRuntimeEntry(null, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(airborne, jump);
        AgentMovementStateRuntime.setInAir(airborne, true);
        assertSame(jump, AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, airborne, 2, 2, (graph, bot, candidate) -> true, (graph, candidate) -> false));

        AgentNavigationGraph.Edge climbExit = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(20, 0), 0, 20, 1, 0, 10, 100, 0);
        AgentRuntimeEntry ropeExit = new AgentRuntimeEntry(null, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(ropeExit, climbExit);
        AgentMovementStateRuntime.setInAir(ropeExit, true);
        assertSame(climbExit, AgentNavigationCommittedEdgeService.reuseCommittedEdge(
                null, ropeExit, 2, 2, (graph, bot, candidate) -> true, (graph, candidate) -> false));
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int launchStepX,
                                                  int portalId,
                                                  int ropeX,
                                                  int ropeTopY,
                                                  int ropeBottomY) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, launchMinX, launchMaxX, launchStepX, portalId, ropeX, ropeTopY, ropeBottomY, 100);
    }
}
