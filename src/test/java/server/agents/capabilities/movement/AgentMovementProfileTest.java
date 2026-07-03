package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementProfileTest {
    @Test
    void baseProfileUsesAgentOwnedPhysicsConfig() {
        AgentMovementProfile profile = AgentMovementProfile.base();

        assertEquals(50, AgentMovementPhysicsConfig.configuredMovementTickMs());
        assertEquals(AgentMovementPhysicsConfig.configuredWalkVelocityPxs(), profile.walkVelocityPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredHorizontalForcePxs(), profile.hForcePxs());
        assertEquals(AgentMovementPhysicsConfig.configuredJumpSpeedPxs(), profile.jumpSpeedPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredRopeJumpSpeedPxs(), profile.ropeJumpSpeedPxs());
        assertEquals(100.0f, AgentMovementPhysicsConfig.configuredClimbSpeedPxs());
        assertEquals(5, AgentMovementKinematicsService.climbStepPerTick());
        assertEquals(8, AgentMovementPhysicsConfig.configuredRopeGrabX());
        assertEquals(16, AgentMovementPhysicsConfig.configuredMaxSnapDrop());
        assertEquals(26, AgentMovementPhysicsConfig.configuredMaxSlopeUp());
        assertEquals(30, AgentMovementPhysicsConfig.configuredStopDist());
        assertEquals(80, AgentMovementPhysicsConfig.configuredFollowDist());
        assertEquals(40, AgentMovementPhysicsConfig.configuredGrindEdgeMargin());
        assertEquals(30, AgentMovementPhysicsConfig.configuredJumpYThreshold());
        assertEquals(4000, AgentMovementPhysicsConfig.configuredTeleportDist());
        assertEquals(600, AgentMovementPhysicsConfig.configuredOutOfBoundsTeleportDist());
        assertEquals(200, AgentMovementPhysicsConfig.configuredFollowYCap());
        assertEquals(8, AgentMovementPhysicsConfig.configuredSwimArrivalRadiusPx());
        assertEquals(500, AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
        assertEquals(30, AgentMovementPhysicsConfig.configuredSwimLevelBandPx());
        assertEquals(120, AgentMovementPhysicsConfig.configuredSwimDownBandPx());
        assertEquals(100, AgentMovementPhysicsConfig.configuredSwimJumpTriggerDyPx());
    }

    @Test
    void profileScalesPhysicsFromAgentOwnedBaseline() {
        AgentMovementProfile profile = new AgentMovementProfile(150, 120);

        assertEquals(AgentMovementPhysicsConfig.configuredWalkVelocityPxs() * 1.5, profile.walkVelocityPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredHorizontalForcePxs() * 1.5, profile.hForcePxs());
        assertEquals(AgentMovementPhysicsConfig.configuredJumpSpeedPxs() * 1.2f, profile.jumpSpeedPxs(), 0.001f);
        assertEquals(AgentMovementPhysicsConfig.configuredRopeJumpSpeedPxs() * 1.2f, profile.ropeJumpSpeedPxs(), 0.001f);
    }

    @Test
    void agentKinematicsExposesJumpProfileCalculations() {
        AgentMovementProfile profile = AgentMovementProfile.base();

        assertTrue(AgentMovementKinematicsService.jumpForcePerTick(profile) > 0.0f);
        assertTrue(AgentMovementKinematicsService.ropeJumpForcePerTick(profile) > 0.0f);
        assertTrue(AgentMovementKinematicsService.gravityPerTick() > 0.0f);
        assertTrue(AgentMovementKinematicsService.calculateMaxJumpHeight(profile) > 0.0f);
    }
}
