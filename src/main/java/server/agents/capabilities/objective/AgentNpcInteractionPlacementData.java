package server.agents.capabilities.objective;

import java.awt.Point;
import java.util.List;

/** Fully resolved, plan-supplied placement inputs for one NPC interaction. */
public record AgentNpcInteractionPlacementData(int interactionRangePx,
        List<Point> anchors, List<Point> legacyAnchors, Integer trafficBiasX,
        boolean dynamicSpread, boolean distinguishInteractionStages,
        Point placementCenterOffset, int placementRadiusPx) {
    public AgentNpcInteractionPlacementData {
        if (interactionRangePx <= 0) throw new IllegalArgumentException("interaction range must be positive");
        anchors = copy(anchors);
        legacyAnchors = copy(legacyAnchors);
        placementCenterOffset = placementCenterOffset == null ? new Point() : new Point(placementCenterOffset);
        if (placementRadiusPx <= 0) throw new IllegalArgumentException("placement radius must be positive");
    }
    public static AgentNpcInteractionPlacementData direct(int rangePx) {
        return new AgentNpcInteractionPlacementData(
                rangePx, List.of(), List.of(), null, false, false, new Point(), rangePx);
    }
    private static List<Point> copy(List<Point> points) {
        return points == null ? List.of() : points.stream().map(Point::new).toList();
    }
}
