package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentNavigationProbeTest {
    @Test
    void formatsLastBuildReportForLoadedMap() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100000000);

        List<String> lines = AgentNavigationProbe.lastBuildReport(map);

        assertFalse(lines.isEmpty());
    }
}
