package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentShopApproachPolicyTest {
    @Test
    void shouldCalculateManhattanDistance() {
        assertEquals(35, AgentShopApproachPolicy.manhattan(new Point(10, 20), new Point(-5, 0)));
    }

    @Test
    void shouldReturnNoCandidatesForMissingInputs() {
        assertTrue(AgentShopApproachPolicy.footholdCandidatesNear(null, List.of(), 100).isEmpty());
        assertTrue(AgentShopApproachPolicy.footholdCandidatesNear(new Point(0, 0), null, 100).isEmpty());
    }

    @Test
    void shouldGenerateCandidatesOnHorizontalFootholdsInsideRadius() {
        Foothold foothold = new Foothold(new Point(0, 100), new Point(200, 100), 1);

        List<Point> candidates = AgentShopApproachPolicy.footholdCandidatesNear(
                new Point(100, 100), List.of(foothold), 20);

        assertEquals(List.of(new Point(80, 100), new Point(90, 100), new Point(100, 100),
                new Point(110, 100), new Point(120, 100)), candidates);
    }

    @Test
    void shouldInterpolateSlopedFootholdsAndSkipWalls() {
        Foothold slope = new Foothold(new Point(0, 100), new Point(100, 200), 1);
        Foothold wall = new Foothold(new Point(50, 100), new Point(50, 200), 2);

        List<Point> candidates = AgentShopApproachPolicy.footholdCandidatesNear(
                new Point(50, 150), List.of(slope, wall), 0);

        assertEquals(List.of(new Point(50, 150)), candidates);
        assertFalse(candidates.contains(new Point(50, 100)));
    }
}
