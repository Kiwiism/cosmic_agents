package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class AgentSpawnPositionServiceTest {
    @Test
    void returnsDesiredPositionWhenMapIsMissing() {
        Point desired = new Point(10, 20);

        Point resolved = AgentSpawnPositionService.resolveSpawnPosition(null, desired, (map, point) -> new Point(1, 2));

        assertEquals(desired, resolved);
    }

    @Test
    void returnsNullWhenDesiredPositionIsMissing() {
        Point resolved = AgentSpawnPositionService.resolveSpawnPosition(null, null, (map, point) -> new Point(1, 2));

        assertNull(resolved);
    }

    @Test
    void looksForGroundOnePixelAboveDesiredPosition() {
        Point desired = new Point(40, 80);
        Point ground = new Point(40, 75);
        MapleMap map = mock(MapleMap.class);
        AtomicReference<Point> lookupPoint = new AtomicReference<>();

        Point resolved = AgentSpawnPositionService.resolveSpawnPosition(
                map,
                desired,
                (ignored, point) -> {
                    lookupPoint.set(point);
                    return ground;
                });

        assertEquals(new Point(40, 79), lookupPoint.get());
        assertEquals(ground, resolved);
    }

    @Test
    void fallsBackToDesiredPositionWhenGroundIsMissing() {
        Point desired = new Point(40, 80);
        MapleMap map = mock(MapleMap.class);

        Point resolved = AgentSpawnPositionService.resolveSpawnPosition(map, desired, (ignored, point) -> null);

        assertEquals(desired, resolved);
    }
}
