package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGroundMovementServiceTest {
    @Test
    void updateStepXSetsWasMovingWhenOutsideFollowDistance() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        BotEntry entry = new BotEntry(null, null, null);

        int step = AgentGroundMovementService.updateStepX(entry, map, 0, 200, 25, 80);

        assertEquals(AgentMovementKinematicsService.walkStep(map), step);
        assertTrue(AgentBotMovementStateRuntime.wasMovingX(entry));
    }

    @Test
    void updateStepXClearsWasMovingWhenInsideStopDistance() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMovementStateRuntime.setWasMovingX(entry, true);

        int step = AgentGroundMovementService.updateStepX(entry, map, 0, 20, 25, 80);

        assertEquals(0, step);
        assertFalse(AgentBotMovementStateRuntime.wasMovingX(entry));
    }

    @Test
    void calcStepXKeepsFastProfileFullStepWhileMoving() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        AgentMovementProfile profile = new AgentMovementProfile(140, 100);

        int step = AgentGroundMovementService.calcStepX(map, profile, 0, 60, true, 25, 80);

        assertEquals(AgentMovementKinematicsService.walkStep(map, profile), step);
    }
}
