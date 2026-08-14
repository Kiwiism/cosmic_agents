package server.agents.field;

import java.awt.Point;

/** Generated candidate position inside a farming cell. */
public record AgentFarmingAnchor(String anchorId, Point position, int score) {
    public AgentFarmingAnchor {
        if (anchorId == null || anchorId.isBlank() || position == null || score < 0) {
            throw new IllegalArgumentException("Valid farming anchor identity, position, and score are required");
        }
        position = new Point(position);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
