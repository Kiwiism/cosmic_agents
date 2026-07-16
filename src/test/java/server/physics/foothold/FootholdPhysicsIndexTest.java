package server.physics.foothold;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FootholdPhysicsIndexTest {
    @Test
    void indexesByIdAndFindsNearestGroundWithPreciseSlope() {
        FootholdSegment upper = segment(1, 0, 0, -100, 100, 100, 80);
        FootholdSegment lower = segment(2, 0, 0, -100, 200, 100, 200);
        FootholdPhysicsIndex index = new FootholdPhysicsIndex(List.of(upper, lower));

        assertSame(upper, index.foothold(1));
        assertSame(upper, index.findBelow(50.0, 0.0));
        assertEquals(85.0, upper.groundY(50.0), 1.0e-9);
        assertSame(lower, index.findBelow(50.0, 90.0));
        assertNull(index.findBelow(500.0, 0.0));
    }

    @Test
    void computesJourneyStyleBoundsAndRejectsDuplicateIds() {
        FootholdSegment floor = segment(1, 0, 0, -100, 100, 100, 100);
        FootholdPhysicsIndex index = new FootholdPhysicsIndex(List.of(floor));

        assertEquals(-75.0, index.bounds().left());
        assertEquals(75.0, index.bounds().right());
        assertEquals(-200.0, index.bounds().top());
        assertEquals(200.0, index.bounds().bottom());
        assertThrows(IllegalArgumentException.class,
                () -> new FootholdPhysicsIndex(List.of(floor, floor)));
    }

    @Test
    void resolvesTrueEdgesAcrossLongConnectedPlatform() {
        FootholdSegment first = segment(1, 0, 2, 0, 100, 50, 100);
        FootholdSegment middle = segment(2, 1, 3, 50, 100, 100, 100);
        FootholdSegment last = segment(3, 2, 0, 100, 100, 150, 100);
        FootholdPhysicsIndex index = new FootholdPhysicsIndex(List.of(first, middle, last));

        assertEquals(0.0, index.edgeBoundary(2, true));
        assertEquals(150.0, index.edgeBoundary(2, false));
    }

    private static FootholdSegment segment(int id, int previous, int next,
                                           double x1, double y1, double x2, double y2) {
        return new FootholdSegment(id, previous, next, 1, 0,
                false, x1, y1, x2, y2);
    }
}
