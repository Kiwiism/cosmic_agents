package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementSkillLandingServiceTest {
    @Test
    void resolvesHorizontalTeleportOntoNearbyGround() {
        MapleMap map = mapWithFootholds(
                new Foothold(new Point(100, 100), new Point(300, 100), 1));

        AgentMovementSkillLandingService.Landing landing =
                AgentMovementSkillLandingService.resolveTeleportLanding(
                        map, new Point(100, 100), 1, 0);

        assertEquals(new Point(250, 100), landing.point());
        assertEquals(1, landing.footholdId());
    }

    @Test
    void resolvesVerticalTeleportToNearestPlatformInDirection() {
        MapleMap map = mapWithFootholds(
                new Foothold(new Point(0, 100), new Point(300, 100), 1),
                new Foothold(new Point(0, 20), new Point(300, 20), 2),
                new Foothold(new Point(0, -30), new Point(300, -30), 3));

        AgentMovementSkillLandingService.Landing landing =
                AgentMovementSkillLandingService.resolveTeleportLanding(
                        map, new Point(150, 100), 0, -1);

        assertEquals(new Point(150, 20), landing.point());
        assertEquals(2, landing.footholdId());
    }

    @Test
    void rejectsDiagonalTeleport() {
        MapleMap map = mapWithFootholds(
                new Foothold(new Point(0, 100), new Point(300, 100), 1));

        assertNull(AgentMovementSkillLandingService.resolveTeleportLanding(
                map, new Point(100, 100), 1, -1));
    }

    private static MapleMap mapWithFootholds(Foothold... footholds) {
        FootholdTree tree = new FootholdTree(new Point(-1000, -1000), new Point(1000, 1000));
        for (Foothold foothold : footholds) {
            tree.insert(foothold);
        }
        MapleMap map = mock(MapleMap.class);
        when(map.getFootholds()).thenReturn(tree);
        return map;
    }
}
