package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementProfileTest {
    @Test
    void baseProfileUsesAgentOwnedPhysicsConfig() {
        AgentMovementProfile profile = AgentMovementProfile.base();

        assertEquals(AgentMovementPhysicsConfig.configuredWalkVelocityPxs(), profile.walkVelocityPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredHorizontalForcePxs(), profile.hForcePxs());
        assertEquals(AgentMovementPhysicsConfig.configuredJumpSpeedPxs(), profile.jumpSpeedPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredRopeJumpSpeedPxs(), profile.ropeJumpSpeedPxs());
        assertEquals(8, AgentMovementPhysicsConfig.configuredRopeGrabX());
        assertEquals(16, AgentMovementPhysicsConfig.configuredMaxSnapDrop());
        assertEquals(26, AgentMovementPhysicsConfig.configuredMaxSlopeUp());
    }

    @Test
    void profileScalesPhysicsFromAgentOwnedBaseline() {
        AgentMovementProfile profile = new AgentMovementProfile(150, 120);

        assertEquals(AgentMovementPhysicsConfig.configuredWalkVelocityPxs() * 1.5, profile.walkVelocityPxs());
        assertEquals(AgentMovementPhysicsConfig.configuredHorizontalForcePxs() * 1.5, profile.hForcePxs());
        assertEquals(AgentMovementPhysicsConfig.configuredJumpSpeedPxs() * 1.2f, profile.jumpSpeedPxs(), 0.001f);
        assertEquals(AgentMovementPhysicsConfig.configuredRopeJumpSpeedPxs() * 1.2f, profile.ropeJumpSpeedPxs(), 0.001f);
    }
}
