package server.agents.capabilities.movement;

import java.awt.Point;

public final class AgentPositionService {
    private AgentPositionService() {
    }

    public static boolean isNear(Point source, Point target, int distance) {
        return source != null && target != null
                && Math.abs(source.x - target.x) <= distance
                && Math.abs(source.y - target.y) <= distance;
    }
}
