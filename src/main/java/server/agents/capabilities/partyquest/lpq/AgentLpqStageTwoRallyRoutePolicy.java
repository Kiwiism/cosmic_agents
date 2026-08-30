package server.agents.capabilities.partyquest.lpq;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

/** LPQ-only committed ascent overlays from Stage 2's lower junction to the balloon. */
final class AgentLpqStageTwoRallyRoutePolicy {
    static final int LEFT_BRANCH = 1;
    static final int RIGHT_BRANCH = 2;

    private static final int STAGE_TWO_MAP = 922_010_200;
    private static final List<Integer> LEFT = List.of(
            21, 20, 19, 18, 47, 11, 45, 10, 9, 8, 7, 6, 5, 4, 3);
    private static final List<Integer> RIGHT = List.of(
            23, 22, 46, 17, 16, 15, 14, 13, 12, 11, 45, 10, 9, 8, 7, 6, 5, 4, 3);

    private AgentLpqStageTwoRallyRoutePolicy() { }

    static Point nextWaypoint(MapleMap map, Point position, AgentLpqMemberState member) {
        if (map == null || map.getId() != STAGE_TWO_MAP || position == null || member == null) {
            return null;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map);
        if (graph == null) graph = AgentNavigationGraphService.getGraph(map);
        if (graph == null) return null;

        // A character hanging on a rope still has a platform below it. The generic
        // lookup grounds first and can therefore report that lower platform, making
        // the overlay repeatedly retarget the same rope. Prefer an authored rope
        // region whenever the current position is actually inside one.
        int currentRegion = graph.findRopeRegionId(position);
        if (currentRegion < 0) currentRegion = graph.findRegionId(map, position);
        if (currentRegion < 0) return null;
        if (member.stageTwoRallyBranch() == 0) {
            member.selectStageTwoRallyBranch(nearestBranch(graph, position));
        }
        int nextRegion = nextRegionId(member.stageTwoRallyBranch(), currentRegion);
        return regionAnchor(graph.getRegion(nextRegion));
    }

    static int nextRegionId(int branch, int currentRegion) {
        // Rope 48 has an authored CLIMB edge only to foothold 21. If an Agent had
        // preselected the right branch, redirecting it from 21 to 23 produced the
        // observed 48 -> 21 -> 23 oscillation. Once the character lands on either
        // valid upper branch, follow that branch instead of its earlier preference.
        if (currentRegion == 48) return LEFT.getFirst();
        List<Integer> route = LEFT.contains(currentRegion) ? LEFT
                : RIGHT.contains(currentRegion) ? RIGHT : route(branch);
        int index = route.indexOf(currentRegion);
        if (index < 0) return route.getFirst();
        return index + 1 < route.size() ? route.get(index + 1) : -1;
    }

    static List<Integer> route(int branch) {
        return branch == RIGHT_BRANCH ? RIGHT : LEFT;
    }

    private static int nearestBranch(AgentNavigationGraph graph, Point position) {
        Point left = regionAnchor(graph.getRegion(LEFT.getFirst()));
        Point right = regionAnchor(graph.getRegion(RIGHT.getFirst()));
        if (left == null) return RIGHT_BRANCH;
        if (right == null) return LEFT_BRANCH;
        return position.distanceSq(left) <= position.distanceSq(right)
                ? LEFT_BRANCH : RIGHT_BRANCH;
    }

    private static Point regionAnchor(AgentNavigationGraph.Region region) {
        if (region == null) return null;
        int centerX = region.minX + (region.maxX - region.minX) / 2;
        if (region.isRopeRegion) {
            return new Point(centerX, region.minY + (region.maxY - region.minY) / 2);
        }
        if (region.segments.isEmpty()) return null;
        return region.segments.stream()
                .filter(segment -> segment.containsX(centerX))
                .findFirst()
                .orElse(region.segments.getFirst())
                .pointAt(centerX);
    }
}
