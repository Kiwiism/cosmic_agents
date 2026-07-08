package server.agents.capabilities.reactor;

import java.awt.Point;

public record AgentReactorTarget(
        int objectId,
        int reactorId,
        String reactorName,
        Point reactorPosition,
        Point targetPosition,
        byte state,
        int reactorType) {

    public AgentReactorTarget {
        if (reactorPosition != null) {
            reactorPosition = new Point(reactorPosition);
        }
        if (targetPosition != null) {
            targetPosition = new Point(targetPosition);
        }
    }
}
