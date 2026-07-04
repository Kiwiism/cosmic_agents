package server.agents.capabilities.movement;

import server.maps.Foothold;

import java.awt.Point;

public record AgentPostLandingJump(AgentJumpLanding landing,
                                   Point finalPoint,
                                   Foothold finalFoothold,
                                   boolean lostGround) {
}
