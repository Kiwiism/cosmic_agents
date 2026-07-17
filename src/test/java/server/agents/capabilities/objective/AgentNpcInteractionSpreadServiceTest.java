package server.agents.capabilities.objective;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNpcInteractionSpreadServiceTest {
    @Test
    void spreadsSlotsAcrossCurrentFootholdWithinNpcRangeAndEdgeInsets() {
        Foothold foothold = new Foothold(new Point(0, 100), new Point(600, 100), 1);

        List<Point> candidates = AgentNpcInteractionSpreadService.candidates(
                foothold, new Point(300, 0), 300);

        assertTrue(candidates.size() >= 20);
        assertTrue(candidates.stream().allMatch(point -> point.x >= 12 && point.x <= 588));
        assertTrue(candidates.stream().allMatch(point -> point.distanceSq(300, 0) <= 90_000L));
        assertEquals(candidates.stream().map(point -> point.x).distinct().count(), candidates.size());
    }

    @Test
    void followsSlopeAndRejectsFootholdThatNeverEntersClickRange() {
        Foothold slope = new Foothold(new Point(0, 200), new Point(240, 80), 2);
        List<Point> candidates = AgentNpcInteractionSpreadService.candidates(
                slope, new Point(200, 60), 100);

        assertTrue(candidates.size() >= 2);
        assertTrue(candidates.stream().allMatch(point -> point.distanceSq(200, 60) <= 10_000L));
        assertEquals(List.of(), AgentNpcInteractionSpreadService.candidates(
                slope, new Point(2_000, 2_000), 100));
    }

    @Test
    void distributesWaitingSlotsAcrossNearbyPlatformsWithoutUsingVerticalFootholdWalls() {
        Foothold lower = new Foothold(new Point(0, 220), new Point(360, 220), 1);
        Foothold middle = new Foothold(new Point(80, 140), new Point(300, 140), 2);
        Foothold upper = new Foothold(new Point(120, 70), new Point(260, 70), 3);
        Foothold wall = new Foothold(new Point(180, 40), new Point(180, 240), 4);

        List<Point> candidates = AgentNpcInteractionSpreadService.candidates(
                List.of(lower, middle, upper, wall), new Point(190, 60), 220);

        assertTrue(candidates.stream().anyMatch(point -> point.y == 220));
        assertTrue(candidates.stream().anyMatch(point -> point.y == 140));
        assertTrue(candidates.stream().anyMatch(point -> point.y == 70));
        assertTrue(candidates.stream().noneMatch(point -> point.y > 220));
    }

    @Test
    void weightsNearestHalfTowardArrivalSideWithoutRemovingFarSlots() {
        Point left = new Point(0, 100);
        Point middleLeft = new Point(24, 100);
        Point middleRight = new Point(48, 100);
        Point right = new Point(72, 100);

        List<Point> pool = AgentNpcInteractionSpreadService.selectionPool(
                List.of(left, middleLeft, middleRight, right), new Point(-20, 100));

        assertEquals(3L, pool.stream().filter(left::equals).count());
        assertEquals(3L, pool.stream().filter(middleLeft::equals).count());
        assertEquals(1L, pool.stream().filter(middleRight::equals).count());
        assertEquals(1L, pool.stream().filter(right::equals).count());
    }

    @Test
    void includesRopeAndLadderHangingSlotsOnlyWhenInsideNpcRange() {
        Rope rope = new Rope(100, 20, 260, false);
        Rope ladder = new Rope(220, 40, 280, true);
        Rope tooFar = new Rope(800, 20, 260, false);
        Point npc = new Point(150, 100);

        List<Point> candidates = AgentNpcInteractionSpreadService.climbableCandidates(
                List.of(rope, ladder, tooFar), npc, 180);

        assertTrue(candidates.stream().anyMatch(point -> point.x == rope.x()));
        assertTrue(candidates.stream().anyMatch(point -> point.x == ladder.x()));
        assertTrue(candidates.stream().noneMatch(point -> point.x == tooFar.x()));
        assertTrue(candidates.stream().allMatch(point -> point.distanceSq(npc) <= 32_400L));
        assertTrue(candidates.stream().allMatch(point -> point.y > 20 && point.y < 280));
    }

}
