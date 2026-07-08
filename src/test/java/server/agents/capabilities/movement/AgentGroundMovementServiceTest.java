package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGroundMovementServiceTest {
    @Test
    void updateStepXSetsWasMovingWhenOutsideFollowDistance() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        int step = AgentGroundMovementService.updateStepX(entry, map, 0, 200, 25, 80);

        assertEquals(AgentMovementKinematicsService.walkStep(map), step);
        assertTrue(AgentMovementStateRuntime.wasMovingX(entry));
    }

    @Test
    void updateStepXClearsWasMovingWhenInsideStopDistance() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMovementStateRuntime.setWasMovingX(entry, true);

        int step = AgentGroundMovementService.updateStepX(entry, map, 0, 20, 25, 80);

        assertEquals(0, step);
        assertFalse(AgentMovementStateRuntime.wasMovingX(entry));
    }

    @Test
    void calcStepXKeepsFastProfileFullStepWhileMoving() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        AgentMovementProfile profile = new AgentMovementProfile(140, 100);

        int step = AgentGroundMovementService.calcStepX(map, profile, 0, 60, true, 25, 80);

        assertEquals(AgentMovementKinematicsService.walkStep(map, profile), step);
    }
}
