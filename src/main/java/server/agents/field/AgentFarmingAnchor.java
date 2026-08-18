package server.agents.field;

import java.awt.Point;

/** Generated station position and its one-dimensional farming territory inside a cell. */
public record AgentFarmingAnchor(
        String anchorId,
        Point position,
        int score,
        int territoryMinX,
        int territoryMaxX) {
    public AgentFarmingAnchor {
        if (anchorId == null || anchorId.isBlank() || position == null || score < 0
                || territoryMinX > territoryMaxX || position.x < territoryMinX
                || position.x > territoryMaxX) {
            throw new IllegalArgumentException("Valid farming anchor identity, position, and score are required");
        }
        position = new Point(position);
    }

    /** Compatibility constructor for authored/test anchors without an explicit territory. */
    public AgentFarmingAnchor(String anchorId, Point position, int score) {
        this(anchorId, position, score, position.x, position.x);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
