package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentJumpActionServiceTest {
    @Test
    void resolveAirVelocityReturnsZeroForVerticalJump() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);

        assertEquals(0, AgentJumpActionService.resolveAirVelocityX(map, AgentMovementProfile.base(), 0));
    }

    @Test
    void resolveAirVelocityUsesSignedWalkStep() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        AgentMovementProfile profile = new AgentMovementProfile(150, 100);
        int walkStep = AgentMovementKinematicsService.walkStep(map, profile);

        assertEquals(walkStep, AgentJumpActionService.resolveAirVelocityX(map, profile, 40));
        assertEquals(-walkStep, AgentJumpActionService.resolveAirVelocityX(map, profile, -40));
    }
}
