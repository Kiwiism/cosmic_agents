package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.maps.MapleMap;
import server.maps.Portal;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqAuthoredFlowPreflightTest {
    private record Exit(int sourceMapId, int portalId, int destinationMapId) { }

    @Test
    void everyFixedStageAndRoomExitMatchesTheAuthoredPortalDestination() {
        List<Exit> exits = List.of(
                new Exit(922_010_100, 2, 922_010_200),
                new Exit(922_010_200, 2, 922_010_300),
                new Exit(922_010_201, 2, 922_010_200),
                new Exit(922_010_300, 2, 922_010_400),
                new Exit(922_010_400, 7, 922_010_500),
                new Exit(922_010_401, 2, 922_010_400),
                new Exit(922_010_402, 2, 922_010_400),
                new Exit(922_010_403, 2, 922_010_400),
                new Exit(922_010_404, 2, 922_010_400),
                new Exit(922_010_405, 2, 922_010_400),
                new Exit(922_010_500, 8, 922_010_600),
                new Exit(922_010_501, 2, 922_010_500),
                new Exit(922_010_502, 2, 922_010_500),
                new Exit(922_010_503, 2, 922_010_500),
                new Exit(922_010_504, 2, 922_010_500),
                new Exit(922_010_505, 2, 922_010_500),
                new Exit(922_010_506, 2, 922_010_500),
                new Exit(922_010_600, 47, 922_010_700),
                new Exit(922_010_700, 2, 922_010_800),
                new Exit(922_010_800, 2, 922_010_900));

        for (Exit exit : exits) {
            MapleMap map = AgentNavigationMapLoader.loadMapGeometry(exit.sourceMapId());
            Portal portal = map.getPortal(exit.portalId());
            assertNotNull(portal, () -> "missing portal " + exit);
            assertEquals(exit.destinationMapId(), portal.getTargetMapId(),
                    () -> "wrong portal destination " + exit);
            Integer fixedPortal = AgentLpqExitRoutePolicy.portalId(
                    exit.sourceMapId(), exit.destinationMapId());
            if (exit.sourceMapId() != 922_010_600) {
                assertEquals(exit.portalId(), fixedPortal, () -> "missing fixed exit " + exit);
            }
        }
    }

    @Test
    void everyStageFiveRecoveryExitReturnsToTheCurrentStageMainMap() {
        for (int roomMapId : List.of(922_010_501, 922_010_506)) {
            MapleMap room = AgentNavigationMapLoader.loadMapGeometry(roomMapId);
            for (int portalId : AgentLpqExitRoutePolicy.portalIds(roomMapId, 922_010_500)) {
                Portal portal = room.getPortal(portalId);
                assertNotNull(portal, () -> "missing recovery portal " + portalId
                        + " in room " + roomMapId);
                assertEquals(922_010_500, portal.getTargetMapId());
            }
        }
    }

    @Test
    void stageSevenContainsExactlyTheThreeAuthoredTriggerMobs() {
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP)
                .getData("Map/Map9/922010700.img");
        assertNotNull(map);
        Data life = map.getChildByPath("life");
        assertNotNull(life);
        Set<Integer> configured = new java.util.LinkedHashSet<>();
        for (Data entry : life) {
            if ("m".equals(DataTool.getString("type", entry, ""))) {
                configured.add(Integer.parseInt(DataTool.getString("id", entry, "0")));
            }
        }

        assertTrue(configured.containsAll(AgentLpqDefinition.STAGE_7_TRIGGER_MOBS));
        assertEquals(3, AgentLpqDefinition.STAGE_7_TRIGGER_MOBS.size());
    }

    @Test
    void stageSevenContainsExactlyThreeAuthoredTriggerReactors() {
        Data map = DataProviderFactory.getDataProvider(WZFiles.MAP)
                .getData("Map/Map9/922010700.img");
        assertNotNull(map);
        Data reactors = map.getChildByPath("reactor");
        assertNotNull(reactors);

        long triggerReactors = reactors.getChildren().stream()
                .filter(reactor -> DataTool.getInt("id", reactor, 0)
                        == AgentLpqDefinition.STAGE_7_TRIGGER_REACTOR)
                .count();
        assertEquals(3L, triggerReactors);
    }
}
