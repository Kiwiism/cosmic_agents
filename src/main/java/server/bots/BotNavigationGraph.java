package server.bots;

import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BotNavigationGraph implements Serializable {
    enum EdgeType {
        WALK,
        JUMP,
        DROP,
        CLIMB,
        PORTAL,
        FLASH_JUMP,
        TELEPORT
    }

    static final class Segment implements Serializable {
        final int footholdId;
        final int x1;
        final int y1;
        final int x2;
        final int y2;
        final int minX;
        final int maxX;

        Segment(Foothold foothold) {
            this.footholdId = foothold.getId();
            this.x1 = foothold.getX1();
            this.y1 = foothold.getY1();
            this.x2 = foothold.getX2();
            this.y2 = foothold.getY2();
            this.minX = Math.min(x1, x2);
            this.maxX = Math.max(x1, x2);
        }

        boolean containsX(int x) {
            return x >= minX && x <= maxX;
        }

        int clampX(int x) {
            if (x < minX) {
                return minX;
            }
            if (x > maxX) {
                return maxX;
            }
            return x;
        }

        Point pointAt(int x) {
            int clampedX = clampX(x);
            if (x1 == x2) {
                return new Point(clampedX, Math.min(y1, y2));
            }

            double ratio = (clampedX - x1) / (double) (x2 - x1);
            int y = (int) Math.round(y1 + (y2 - y1) * ratio);
            return new Point(clampedX, y);
        }
    }

    static final class Region implements Serializable {
        final int id;
        final List<Segment> segments;
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;

        Region(int id, List<Segment> segments) {
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Bot nav region requires at least one segment");
            }

            this.id = id;
            this.segments = new ArrayList<>(segments);

            int regionMinX = Integer.MAX_VALUE;
            int regionMaxX = Integer.MIN_VALUE;
            int regionMinY = Integer.MAX_VALUE;
            int regionMaxY = Integer.MIN_VALUE;
            for (Segment segment : segments) {
                regionMinX = Math.min(regionMinX, segment.minX);
                regionMaxX = Math.max(regionMaxX, segment.maxX);
                regionMinY = Math.min(regionMinY, Math.min(segment.y1, segment.y2));
                regionMaxY = Math.max(regionMaxY, Math.max(segment.y1, segment.y2));
            }

            this.minX = regionMinX;
            this.maxX = regionMaxX;
            this.minY = regionMinY;
            this.maxY = regionMaxY;
        }

        int width() {
            return Math.max(0, maxX - minX);
        }

        Point leftPoint() {
            return pointAt(minX);
        }

        Point centerPoint() {
            return pointAt(minX + width() / 2);
        }

        Point rightPoint() {
            return pointAt(maxX);
        }

        Point pointAt(int x) {
            Segment bestSegment = findBestSegment(x);
            return bestSegment.pointAt(x);
        }

        private Segment findBestSegment(int x) {
            Segment best = segments.get(0);
            int bestDistance = distanceToSegment(best, x);
            for (int i = 1; i < segments.size(); i++) {
                Segment segment = segments.get(i);
                int distance = distanceToSegment(segment, x);
                if (distance < bestDistance) {
                    best = segment;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private int distanceToSegment(Segment segment, int x) {
            if (segment.containsX(x)) {
                return 0;
            }
            return x < segment.minX ? segment.minX - x : x - segment.maxX;
        }
    }

    static final class Edge implements Serializable {
        final int fromRegionId;
        final int toRegionId;
        final EdgeType type;
        final Point startPoint;
        final Point endPoint;
        final int launchStepX;
        final int portalId;
        final int ropeX;
        final int ropeTopY;
        final int ropeBottomY;
        final int cost;

        Edge(int fromRegionId,
             int toRegionId,
             EdgeType type,
             Point startPoint,
             Point endPoint,
             int launchStepX,
             int portalId,
             int ropeX,
             int ropeTopY,
             int ropeBottomY,
             int cost) {
            this.fromRegionId = fromRegionId;
            this.toRegionId = toRegionId;
            this.type = type;
            this.startPoint = new Point(startPoint);
            this.endPoint = new Point(endPoint);
            this.launchStepX = launchStepX;
            this.portalId = portalId;
            this.ropeX = ropeX;
            this.ropeTopY = ropeTopY;
            this.ropeBottomY = ropeBottomY;
            this.cost = cost;
        }
    }

    final int mapId;
    final int version;
    final List<Region> regions;
    final Map<Integer, Region> regionsById;
    final Map<Integer, Integer> regionIdByFootholdId;
    final Map<Integer, List<Edge>> outgoingByRegionId;

    BotNavigationGraph(int mapId,
                       int version,
                       List<Region> regions,
                       Map<Integer, Region> regionsById,
                       Map<Integer, Integer> regionIdByFootholdId,
                       Map<Integer, List<Edge>> outgoingByRegionId) {
        this.mapId = mapId;
        this.version = version;
        this.regions = new ArrayList<>(regions);
        this.regionsById = new HashMap<>(regionsById);
        this.regionIdByFootholdId = new HashMap<>(regionIdByFootholdId);
        this.outgoingByRegionId = new HashMap<>();
        for (Map.Entry<Integer, List<Edge>> entry : outgoingByRegionId.entrySet()) {
            this.outgoingByRegionId.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    Region getRegion(int regionId) {
        return regionsById.get(regionId);
    }

    List<Edge> getOutgoing(int regionId) {
        return outgoingByRegionId.getOrDefault(regionId, List.of());
    }

    int findRegionId(MapleMap map, Point position) {
        if (position == null || map.getFootholds() == null) {
            return -1;
        }

        Foothold foothold = BotPhysicsEngine.findGroundFoothold(map, position);
        if (foothold == null) {
            return -1;
        }

        return regionIdByFootholdId.getOrDefault(foothold.getId(), -1);
    }
}
