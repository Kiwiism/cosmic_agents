package server.agents.capabilities.movement;

import server.maps.Foothold;

import java.awt.Point;

public record AgentJumpLanding(Point point,
                               Foothold foothold,
                               double incomingDeltaX,
                               double incomingDeltaY,
                               int timeMs) {
}
