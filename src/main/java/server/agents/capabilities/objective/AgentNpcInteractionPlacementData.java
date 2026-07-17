package server.agents.capabilities.objective;

import java.awt.Point;
import java.util.List;

/** Fully resolved, plan-supplied placement inputs for one NPC interaction. */
public record AgentNpcInteractionPlacementData(int interactionRangePx,
        List<Point> anchors, List<Point> legacyAnchors, Integer trafficBiasX,
        boolean dynamicSpread, boolean distinguishInteractionStages) {
    public AgentNpcInteractionPlacementData {
        if (interactionRangePx <= 0) throw new IllegalArgumentException("interaction range must be positive");
        anchors = copy(anchors);
        legacyAnchors = copy(legacyAnchors);
    }
    public static AgentNpcInteractionPlacementData direct(int rangePx) {
        return new AgentNpcInteractionPlacementData(rangePx, List.of(), List.of(), null, false, false);
    }
    private static List<Point> copy(List<Point> points) {
        return points == null ? List.of() : points.stream().map(Point::new).toList();
    }
}
