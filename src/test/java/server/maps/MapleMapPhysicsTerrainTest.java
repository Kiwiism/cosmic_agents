package server.maps;

import org.junit.jupiter.api.Test;
import server.physics.foothold.FootholdSegment;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleMapPhysicsTerrainTest {
    @Test
    void setFootholdsBuildsImmutablePhysicsIndexOnce() {
        FootholdTree tree = new FootholdTree(new Point(-100, -100), new Point(100, 200));
        Foothold foothold = new Foothold(new Point(-100, 100), new Point(100, 80), 7);
        foothold.setLayer(2);
        foothold.setZMass(3);
        foothold.setForbidFallDown(true);
        tree.insert(foothold);
        MapleMap map = new MapleMap(100000000, 0, 1, 100000000, 1.0f);

        map.setFootholds(tree);

        assertNotNull(map.getPhysicsTerrain());
        FootholdSegment segment = map.getPhysicsTerrain().foothold(7);
        assertEquals(2, segment.layer());
        assertEquals(3, segment.zMass());
        assertTrue(segment.forbidFallDown());
        assertEquals(90.0, segment.groundY(0.0), 1.0e-12);
    }
}
