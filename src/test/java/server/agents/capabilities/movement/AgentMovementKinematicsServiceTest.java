package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMovementKinematicsServiceTest {
    @Test
    void baseWalkStepUsesConfiguredMovementProfile() {
        MapleMap map = mock(MapleMap.class);

        assertEquals(
                AgentMovementKinematicsService.walkStep(map, AgentMovementProfile.base()),
                AgentMovementKinematicsService.walkStep(map));
    }

    @Test
    void walkStepScalesWithMovementProfile() {
        MapleMap map = mock(MapleMap.class);

        int base = AgentMovementKinematicsService.walkStep(map, AgentMovementProfile.base());
        int faster = AgentMovementKinematicsService.walkStep(map, new AgentMovementProfile(150, 100));

        assertEquals(Math.round(base * 1.5f), faster);
    }
}
