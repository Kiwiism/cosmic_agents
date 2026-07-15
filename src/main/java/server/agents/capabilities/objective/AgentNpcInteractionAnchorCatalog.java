package server.agents.capabilities.objective;

import java.awt.Point;
import java.util.List;

final class AgentNpcInteractionAnchorCatalog {
    private static final int YOONA_MAP_ID = 1010000;
    private static final int YOONA_NPC_ID = 20100;
    private static final List<Point> YOONA_ANCHORS = List.of(
            new Point(-210, 95),
            new Point(-150, 95),
            new Point(-210, 215),
            new Point(-150, 215));

    private AgentNpcInteractionAnchorCatalog() {
    }

    static Point nearest(int mapId, int npcId, Point origin) {
        List<Point> anchors = anchors(mapId, npcId);
        if (anchors.isEmpty() || origin == null) {
            return null;
        }
        Point nearest = anchors.getFirst();
        double nearestDistanceSq = origin.distanceSq(nearest);
        for (int i = 1; i < anchors.size(); i++) {
            Point candidate = anchors.get(i);
            double distanceSq = origin.distanceSq(candidate);
            if (distanceSq < nearestDistanceSq) {
                nearest = candidate;
                nearestDistanceSq = distanceSq;
            }
        }
        return new Point(nearest);
    }

    static List<Point> anchors(int mapId, int npcId) {
        return mapId == YOONA_MAP_ID && npcId == YOONA_NPC_ID
                ? YOONA_ANCHORS.stream().map(Point::new).toList()
                : List.of();
    }
}
