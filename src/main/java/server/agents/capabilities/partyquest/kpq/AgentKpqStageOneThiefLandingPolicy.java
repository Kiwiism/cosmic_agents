package server.agents.capabilities.partyquest.kpq;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import java.awt.Point;

/** Pure geometry and descent decisions for Stage 1 Rogue landing safety. */
final class AgentKpqStageOneThiefLandingPolicy {
    private AgentKpqStageOneThiefLandingPolicy() {
    }

    static boolean descending(
            boolean previousRopeSample, int previousY, int currentY, int velocityY) {
        return velocityY > 0 || (previousRopeSample && currentY > previousY + 1);
    }

    static Point nearestInteriorPoint(
            Point position, AgentNavigationGraph.Region region, int requestedMarginPx) {
        if (position == null || region == null || region.isRopeRegion || region.width() < 3) {
            return null;
        }
        int margin = Math.min(Math.max(1, requestedMarginPx), Math.max(1, region.width() / 3));
        int minimumX = region.minX + margin;
        int maximumX = region.maxX - margin;
        int targetX = Math.max(minimumX, Math.min(maximumX, position.x));
        return region.pointAt(targetX);
    }

    static boolean reached(Point position, Point target, int tolerancePx) {
        if (position == null || target == null) return false;
        int tolerance = Math.max(0, tolerancePx);
        return Math.abs(position.x - target.x) <= tolerance
                && Math.abs(position.y - target.y) <= Math.max(24, tolerance);
    }
}
