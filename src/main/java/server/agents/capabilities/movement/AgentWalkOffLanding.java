package server.agents.capabilities.movement;

import java.awt.Point;

public record AgentWalkOffLanding(Point launchPoint,
                                  int launchStepX,
                                  AgentJumpLanding landing,
                                  int travelTimeMs) {
}
