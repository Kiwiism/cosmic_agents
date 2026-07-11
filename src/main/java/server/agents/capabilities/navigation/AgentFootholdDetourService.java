package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sticky within-region foothold-chain detours for branch-shaped launch approaches. */
public final class AgentFootholdDetourService {
    private AgentFootholdDetourService() {
    }

    public static Point waypoint(AgentRuntimeEntry entry,
                                 AgentNavigationGraph graph,
                                 Point agentPosition,
                                 AgentNavigationGraph.Edge edge) {
        Point active = activeWaypoint(entry, agentPosition, edge);
        if (active != null) {
            return active;
        }
        MapleMap map = entry == null ? null : AgentRuntimeIdentityRuntime.botMap(entry);
        AgentNavigationGraph.Region region = graph == null || edge == null ? null : graph.getRegion(edge.fromRegionId);
        if (map == null || region == null || region.isRopeRegion || agentPosition == null) {
            clear(entry);
            return null;
        }

        Point launchPoint = edge.startPoint;
        Foothold current = regionFoothold(map, region, agentPosition);
        Foothold launch = regionFoothold(map, region, launchPoint);
        if (current == null || launch == null || current.getId() == launch.getId()) {
            clear(entry);
            return null;
        }
        List<Foothold> path = walkPath(map, region, current, launch);
        if (path == null || path.size() < 2) {
            clear(entry);
            return null;
        }
        Point crossing = sharedEndpoint(current, path.get(1));
        if (crossing == null) {
            clear(entry);
            return null;
        }
        int awayDirection = Integer.signum(crossing.x - agentPosition.x);
        int launchDirection = Integer.signum(launchPoint.x - agentPosition.x);
        if (awayDirection == 0 || awayDirection == launchDirection) {
            clear(entry);
            return null;
        }

        Point detour = farEndpoint(path.get(1), crossing);
        entry.navigationEdgeState().setFootholdDetour(edge, detour);
        return detour;
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.navigationEdgeState().hasFootholdDetour();
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.navigationEdgeState().clearFootholdDetour();
        }
    }

    private static Point activeWaypoint(AgentRuntimeEntry entry, Point position, AgentNavigationGraph.Edge edge) {
        if (entry == null || position == null || edge == null
                || !entry.navigationEdgeState().footholdDetourMatches(edge)) {
            clear(entry);
            return null;
        }
        Point target = entry.navigationEdgeState().footholdDetourTarget();
        if (target == null) {
            return null;
        }
        int remainingDirection = Integer.signum(target.x - position.x);
        int detourDirection = Integer.signum(target.x - edge.startPoint.x);
        if (remainingDirection == 0 || detourDirection == 0 || remainingDirection != detourDirection) {
            clear(entry);
            return null;
        }
        return target;
    }

    private static List<Foothold> walkPath(MapleMap map,
                                           AgentNavigationGraph.Region region,
                                           Foothold start,
                                           Foothold goal) {
        Set<Integer> inRegion = new HashSet<>();
        for (AgentNavigationGraph.Segment segment : region.segments) {
            inRegion.add(segment.footholdId);
        }
        if (!inRegion.contains(start.getId()) || !inRegion.contains(goal.getId())) {
            return null;
        }
        Map<Integer, Foothold> byId = new HashMap<>();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            byId.put(foothold.getId(), foothold);
        }
        Map<Integer, Integer> previous = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        previous.put(start.getId(), start.getId());
        queue.add(start.getId());
        while (!queue.isEmpty()) {
            int currentId = queue.removeFirst();
            if (currentId == goal.getId()) {
                break;
            }
            Foothold current = byId.get(currentId);
            if (current == null) {
                continue;
            }
            for (int neighborId : new int[]{current.getPrev(), current.getNext()}) {
                Foothold neighbor = byId.get(neighborId);
                if (neighborId <= 0 || previous.containsKey(neighborId) || !inRegion.contains(neighborId)
                        || neighbor == null || !AgentNavigationPhysicsService.canWalkAcrossFootholds(current, neighbor)) {
                    continue;
                }
                previous.put(neighborId, currentId);
                queue.addLast(neighborId);
            }
        }
        if (!previous.containsKey(goal.getId())) {
            return null;
        }
        LinkedList<Foothold> path = new LinkedList<>();
        for (int cursor = goal.getId();; cursor = previous.get(cursor)) {
            path.addFirst(byId.get(cursor));
            if (cursor == start.getId()) {
                return path;
            }
        }
    }

    private static Foothold regionFoothold(MapleMap map,
                                           AgentNavigationGraph.Region region,
                                           Point position) {
        Map<Integer, Foothold> byId = new HashMap<>();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            byId.put(foothold.getId(), foothold);
        }
        Foothold best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (AgentNavigationGraph.Segment segment : region.segments) {
            if (!segment.containsX(position.x)) {
                continue;
            }
            int distance = Math.abs(segment.pointAt(position.x).y - position.y);
            if (distance < bestDistance) {
                best = byId.get(segment.footholdId);
                bestDistance = distance;
            }
        }
        return best != null ? best : AgentGroundingService.findGroundFoothold(map, position);
    }

    private static Point sharedEndpoint(Foothold first, Foothold second) {
        for (Point left : endpoints(first)) {
            for (Point right : endpoints(second)) {
                if (Math.abs(left.x - right.x) <= 3 && Math.abs(left.y - right.y) <= 3) {
                    return left;
                }
            }
        }
        return null;
    }

    private static Point farEndpoint(Foothold foothold, Point near) {
        Point first = new Point(foothold.getX1(), foothold.getY1());
        Point second = new Point(foothold.getX2(), foothold.getY2());
        return Math.abs(first.x - near.x) <= 3 && Math.abs(first.y - near.y) <= 3 ? second : first;
    }

    private static List<Point> endpoints(Foothold foothold) {
        List<Point> result = new ArrayList<>(2);
        result.add(new Point(foothold.getX1(), foothold.getY1()));
        result.add(new Point(foothold.getX2(), foothold.getY2()));
        return result;
    }
}
