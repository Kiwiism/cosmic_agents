package server.agents.capabilities.navigation;

import server.bots.BotPhysicsEngine;
import server.maps.Rope;

/**
 * Agent-owned seam for navigation-facing physics helpers while internals migrate.
 */
public final class AgentNavigationPhysicsService {
    private AgentNavigationPhysicsService() {
    }

    public static int firstClimbableY(Rope rope) {
        return BotPhysicsEngine.firstClimbableY(rope);
    }

    public static boolean isWalkableEndpointStep(int dx, int dy) {
        return BotPhysicsEngine.isWalkableEndpointStep(dx, dy);
    }
}
