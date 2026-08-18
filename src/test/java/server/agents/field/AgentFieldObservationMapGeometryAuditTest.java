package server.agents.field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.agents.runtime.field.AgentFieldSafeSpotPolicy;
import server.maps.MapleMap;

import java.awt.Point;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/** Manual WZ/navgraph preflight for every map exposed by the field observation catalog. */
class AgentFieldObservationMapGeometryAuditTest {
    @Test
    @EnabledIfSystemProperty(named = "agent.field.catalogAudit", matches = "true")
    void everyObservationMapHasSafeStagingReachableFromANearbyPlayerSpawn() {
        if (System.getProperty("wz-path") == null) {
            System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
        }
        List<String> failures = new ArrayList<>();
        for (AgentFieldObservationCatalog.MapPreset preset
                : AgentFieldObservationCatalogRepository.defaultRepository().maps()) {
            try {
                MapleMap map = AgentNavigationMapLoader.loadMapGeometry(preset.mapId());
                AgentNavigationGraph graph = AgentNavigationGraphService.getGraph(
                        map, AgentMovementProfile.base());
                for (int ordinal = 0; ordinal < preset.maximumAgents(); ordinal++) {
                    Point staging = AgentFieldSafeSpotPolicy.staging(map, graph, ordinal);
                    Point entry = AgentFieldSafeSpotPolicy.nearestEntry(map, graph, staging);
                    if (staging == null || entry == null) {
                        failures.add(preset.mapId() + " " + preset.mapName()
                                + " slot " + ordinal + " has no reachable staging/entry pair");
                    }
                }
            } catch (RuntimeException | Error failure) {
                failures.add(preset.mapId() + " " + preset.mapName() + ": " + failure.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join(System.lineSeparator(), failures));
        }
    }
}
