package server.bots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class BotNavigationGraphProvider {
    private static final Logger log = LoggerFactory.getLogger(BotNavigationGraphProvider.class);

    private static final int GRAPH_VERSION = 7;
    private static final int WALK_CONNECTION_GAP_PX = 12;
    private static final int ENDPOINT_ANCHOR_SPACING_PX = 16;
    private static final double REGION_MERGE_MIN_CONTINUATION_COSINE = 0.94;
    private static final Path CACHE_DIR = Path.of("cache", "bot-nav", "v" + GRAPH_VERSION);
    private static final Map<Integer, BotNavigationGraph> GRAPHS = new ConcurrentHashMap<>();

    static BotNavigationGraph getGraph(MapleMap map) {
        return GRAPHS.computeIfAbsent(map.getId(), ignored -> loadOrBuildGraph(map));
    }

    static BotNavigationGraph rebuildGraph(MapleMap map) {
        BotNavigationGraph rebuilt = buildGraph(map);
        GRAPHS.put(map.getId(), rebuilt);
        saveGraph(rebuilt);
        return rebuilt;
    }

    private static BotNavigationGraph loadOrBuildGraph(MapleMap map) {
        BotNavigationGraph cached = loadGraph(map.getId());
        if (cached != null) {
            return cached;
        }

        BotNavigationGraph built = buildGraph(map);
        saveGraph(built);
        return built;
    }

    private static BotNavigationGraph loadGraph(int mapId) {
        Path file = graphFile(mapId);
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            Object loaded = in.readObject();
            if (!(loaded instanceof BotNavigationGraph graph)) {
                return null;
            }
            if (graph.version != GRAPH_VERSION || graph.mapId != mapId) {
                return null;
            }
            return graph;
        } catch (IOException | ClassNotFoundException e) {
            log.debug("Failed to load bot nav graph cache for map {}", mapId, e);
            return null;
        }
    }

    private static void saveGraph(BotNavigationGraph graph) {
        try {
            Files.createDirectories(CACHE_DIR);
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(graphFile(graph.mapId)))) {
                out.writeObject(graph);
            }
        } catch (IOException e) {
            log.debug("Failed to save bot nav graph cache for map {}", graph.mapId, e);
        }
    }

    private static Path graphFile(int mapId) {
        return CACHE_DIR.resolve(mapId + ".bin");
    }

    private static BotNavigationGraph buildGraph(MapleMap map) {
        List<Foothold> footholds = map.getFootholds() == null ? List.of() : map.getFootholds().getAllFootholds();
        Map<Integer, Foothold> footholdsById = new HashMap<>();
        List<Foothold> walkableFootholds = new ArrayList<>();
        for (Foothold foothold : footholds) {
            footholdsById.put(foothold.getId(), foothold);
            if (!foothold.isWall()) {
                walkableFootholds.add(foothold);
            }
        }

        List<BotNavigationGraph.Region> regions = new ArrayList<>();
        Map<Integer, BotNavigationGraph.Region> regionsById = new HashMap<>();
        Map<Integer, Integer> regionIdByFootholdId = new HashMap<>();
        buildRegions(walkableFootholds, footholdsById, regions, regionsById, regionIdByFootholdId);

        Map<Integer, List<Integer>> featureXsByRegionId = buildFeatureXsByRegionId(map, regionIdByFootholdId);
        Map<Integer, List<BotNavigationGraph.Edge>> outgoing = new HashMap<>();
        Set<String> edgeKeys = new HashSet<>();

        for (Foothold foothold : walkableFootholds) {
            addWalkEdges(foothold, footholdsById, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
        }

        for (BotNavigationGraph.Region region : regions) {
            addDropEdges(region, map, regionsById, regionIdByFootholdId, featureXsByRegionId, outgoing, edgeKeys);
            addJumpEdges(region, map, regionsById, regionIdByFootholdId, featureXsByRegionId, outgoing, edgeKeys);
        }

        for (Rope rope : map.getRopes()) {
            addClimbEdges(rope, map, regionsById, regionIdByFootholdId, featureXsByRegionId, outgoing, edgeKeys);
        }

        for (Portal portal : map.getPortals()) {
            addPortalEdges(portal, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
        }

        return new BotNavigationGraph(map.getId(), GRAPH_VERSION, regions, regionsById, regionIdByFootholdId, outgoing);
    }

    private static void buildRegions(List<Foothold> footholds,
                                     Map<Integer, Foothold> footholdsById,
                                     List<BotNavigationGraph.Region> regions,
                                     Map<Integer, BotNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId) {
        UnionFind unionFind = new UnionFind();
        for (Foothold foothold : footholds) {
            unionFind.add(foothold.getId());
        }

        for (Foothold foothold : footholds) {
            unionWalkableFootholds(unionFind, foothold, footholdsById.get(foothold.getPrev()));
            unionWalkableFootholds(unionFind, foothold, footholdsById.get(foothold.getNext()));
        }

        Map<Integer, List<Foothold>> groupedFootholds = new HashMap<>();
        for (Foothold foothold : footholds) {
            groupedFootholds.computeIfAbsent(unionFind.find(foothold.getId()), ignored -> new ArrayList<>()).add(foothold);
        }

        List<List<Foothold>> groups = new ArrayList<>(groupedFootholds.values());
        groups.sort(Comparator
                .comparingInt(BotNavigationGraphProvider::groupMinY)
                .thenComparingInt(BotNavigationGraphProvider::groupMinX));

        int nextRegionId = 1;
        for (List<Foothold> group : groups) {
            group.sort(Comparator
                    .comparingInt(BotNavigationGraphProvider::footholdMinX)
                    .thenComparingInt(foothold -> Math.min(foothold.getY1(), foothold.getY2()))
                    .thenComparingInt(Foothold::getId));

            List<BotNavigationGraph.Segment> segments = new ArrayList<>(group.size());
            for (Foothold foothold : group) {
                segments.add(new BotNavigationGraph.Segment(foothold));
            }

            BotNavigationGraph.Region region = new BotNavigationGraph.Region(nextRegionId++, segments);
            regions.add(region);
            regionsById.put(region.id, region);
            for (Foothold foothold : group) {
                regionIdByFootholdId.put(foothold.getId(), region.id);
            }
        }
    }

    private static void unionWalkableFootholds(UnionFind unionFind, Foothold first, Foothold second) {
        if (!canMergeIntoRegion(first, second)) {
            return;
        }
        unionFind.union(first.getId(), second.getId());
    }

    private static boolean canMergeIntoRegion(Foothold first, Foothold second) {
        if (first == null || second == null || first.isWall() || second.isWall()) {
            return false;
        }

        EndpointConnection connection = sharedEndpointConnection(first, second);
        if (connection == null) {
            return false;
        }

        return isWalkConnection(connection)
                && hasCompatiblePlatformShape(first, second, connection.from);
    }

    private static void addWalkEdges(Foothold foothold,
                                     Map<Integer, Foothold> footholdsById,
                                     Map<Integer, BotNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys) {
        addWalkEdge(foothold, footholdsById.get(foothold.getPrev()), regionsById, regionIdByFootholdId, outgoing, edgeKeys);
        addWalkEdge(foothold, footholdsById.get(foothold.getNext()), regionsById, regionIdByFootholdId, outgoing, edgeKeys);
    }

    private static void addWalkEdge(Foothold fromFoothold,
                                    Foothold targetFoothold,
                                    Map<Integer, BotNavigationGraph.Region> regionsById,
                                    Map<Integer, Integer> regionIdByFootholdId,
                                    Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                    Set<String> edgeKeys) {
        if (fromFoothold == null || targetFoothold == null || targetFoothold.isWall()) {
            return;
        }

        int fromRegionId = regionIdByFootholdId.getOrDefault(fromFoothold.getId(), -1);
        int toRegionId = regionIdByFootholdId.getOrDefault(targetFoothold.getId(), -1);
        if (fromRegionId < 0 || toRegionId < 0 || fromRegionId == toRegionId) {
            return;
        }

        EndpointConnection connection = closestEndpointConnection(fromFoothold, targetFoothold);
        if (connection == null || !isWalkConnection(connection)) {
            return;
        }

        BotNavigationGraph.Region from = regionsById.get(fromRegionId);
        BotNavigationGraph.Region to = regionsById.get(toRegionId);
        if (from == null || to == null) {
            return;
        }

        Point start = from.pointAt(connection.from.x);
        Point end = to.pointAt(connection.to.x);
        int cost = estimateWalkCost(start, end);
        addEdge(from.id, to.id, BotNavigationGraph.EdgeType.WALK, start, end, 0, 0, cost, outgoing, edgeKeys);
        addEdge(to.id, from.id, BotNavigationGraph.EdgeType.WALK, end, start, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addDropEdges(BotNavigationGraph.Region from,
                                     MapleMap map,
                                     Map<Integer, BotNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     Map<Integer, List<Integer>> featureXsByRegionId,
                                     Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys) {
        for (Point anchor : anchorPoints(from, featureXsByRegionId.getOrDefault(from.id, List.of()))) {
            int dropStepX = dropLaunchStep(from, map, anchor);
            BotPhysicsEngine.JumpLanding landing = dropStepX == 0
                    ? BotPhysicsEngine.simulateDownJumpLanding(map, anchor)
                    : BotPhysicsEngine.simulateFallLanding(map, anchor, dropStepX);
            if (landing == null) {
                continue;
            }

            int toRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            BotNavigationGraph.Region below = regionsById.get(toRegionId);
            if (below == null || below.id == from.id) {
                continue;
            }

            if (landing.point().y <= anchor.y + 4) {
                continue;
            }

            int dropCost = estimateDropCost(anchor, landing.point()) + (dropStepX == 0 ? 300 : 0);
            addEdge(from.id, below.id, BotNavigationGraph.EdgeType.DROP, anchor, landing.point(), dropStepX, 0, dropCost, outgoing, edgeKeys);
        }
    }

    private static void addJumpEdges(BotNavigationGraph.Region from,
                                     MapleMap map,
                                     Map<Integer, BotNavigationGraph.Region> regionsById,
                                     Map<Integer, Integer> regionIdByFootholdId,
                                     Map<Integer, List<Integer>> featureXsByRegionId,
                                     Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                     Set<String> edgeKeys) {
        int jumpStep = BotMovementManager.walkStep(map);
        for (Point anchor : anchorPoints(from, featureXsByRegionId.getOrDefault(from.id, List.of()))) {
            for (int launchStepX : new int[]{-jumpStep, 0, jumpStep}) {
                BotMovementManager.JumpLanding landing = BotMovementManager.simulateJumpLanding(map, anchor, launchStepX);
                if (landing == null) {
                    continue;
                }

                int toRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
                BotNavigationGraph.Region to = regionsById.get(toRegionId);
                if (to == null || to.id == from.id) {
                    continue;
                }
                if (Math.abs(landing.point().x - anchor.x) < 6 && Math.abs(landing.point().y - anchor.y) < 6) {
                    continue;
                }

                addEdge(from.id, to.id, BotNavigationGraph.EdgeType.JUMP, anchor, landing.point(), launchStepX, 0,
                        estimateJumpCost(anchor, landing.point()), outgoing, edgeKeys);
            }
        }
    }

    private static void addClimbEdges(Rope rope,
                                      MapleMap map,
                                      Map<Integer, BotNavigationGraph.Region> regionsById,
                                      Map<Integer, Integer> regionIdByFootholdId,
                                      Map<Integer, List<Integer>> featureXsByRegionId,
                                      Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                      Set<String> edgeKeys) {
        Map<Integer, RopeAccess> bottomAccesses = findBottomRopeAccesses(rope, map, regionsById, featureXsByRegionId);
        Map<Integer, RopeExit> topExits = findTopRopeExits(rope, map, regionsById, regionIdByFootholdId);
        if (bottomAccesses.isEmpty() || topExits.isEmpty()) {
            return;
        }

        for (RopeAccess bottom : bottomAccesses.values()) {
            for (RopeExit top : topExits.values()) {
                if (bottom.regionId == top.regionId) {
                    continue;
                }

                int cost = estimateClimbTraversalCost(rope, bottom.point, top.point, top.exitStepX);
                addEdge(bottom.regionId, top.regionId, BotNavigationGraph.EdgeType.CLIMB, bottom.point, top.point,
                        top.exitStepX, 0, rope.x(), rope.topY(), rope.bottomY(), cost, outgoing, edgeKeys);
                addEdge(top.regionId, bottom.regionId, BotNavigationGraph.EdgeType.CLIMB, top.point, bottom.point,
                        0, 0, rope.x(), rope.topY(), rope.bottomY(), cost, outgoing, edgeKeys);
            }
        }
    }

    private static Map<Integer, RopeAccess> findBottomRopeAccesses(Rope rope,
                                                                   MapleMap map,
                                                                   Map<Integer, BotNavigationGraph.Region> regionsById,
                                                                   Map<Integer, List<Integer>> featureXsByRegionId) {
        Map<Integer, RopeAccess> accesses = new HashMap<>();
        for (BotNavigationGraph.Region region : regionsById.values()) {
            for (Point anchor : anchorPoints(region, featureXsByRegionId.getOrDefault(region.id, List.of()))) {
                if (!BotMovementManager.canReachRopeFromGround(map, anchor, rope)) {
                    continue;
                }

                RopeAccess candidate = new RopeAccess(region.id, anchor);
                RopeAccess existing = accesses.get(region.id);
                if (existing == null || scoreBottomRopeAccess(candidate, rope) < scoreBottomRopeAccess(existing, rope)) {
                    accesses.put(region.id, candidate);
                }
            }
        }
        return accesses;
    }

    private static Map<Integer, RopeExit> findTopRopeExits(Rope rope,
                                                           MapleMap map,
                                                           Map<Integer, BotNavigationGraph.Region> regionsById,
                                                           Map<Integer, Integer> regionIdByFootholdId) {
        Map<Integer, RopeExit> exits = new HashMap<>();

        RegionPoint directExit = findNearbyRegionBelow(map, regionsById, regionIdByFootholdId,
                rope.x(), rope.topY() - 1, Math.max(BotMovementManager.cfg.ROPE_GRAB_X, 28));
        if (directExit != null) {
            exits.put(directExit.region.id, new RopeExit(directExit.region.id, directExit.point, 0));
        }

        Point ropeTop = new Point(rope.x(), rope.topY());
        int jumpStep = BotMovementManager.walkStep(map);
        for (int exitStepX : new int[]{-jumpStep, 0, jumpStep}) {
            BotMovementManager.JumpLanding landing = BotMovementManager.simulateRopeJumpLanding(map, ropeTop, exitStepX);
            if (landing == null) {
                continue;
            }

            int regionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            BotNavigationGraph.Region region = regionsById.get(regionId);
            if (region == null) {
                continue;
            }

            RopeExit candidate = new RopeExit(region.id, landing.point(), exitStepX);
            RopeExit existing = exits.get(region.id);
            if (existing == null || scoreTopRopeExit(candidate, rope) < scoreTopRopeExit(existing, rope)) {
                exits.put(region.id, candidate);
            }
        }

        return exits;
    }

    private static RegionPoint findNearbyRegionBelow(MapleMap map,
                                                     Map<Integer, BotNavigationGraph.Region> regionsById,
                                                     Map<Integer, Integer> regionIdByFootholdId,
                                                     int centerX,
                                                     int probeY,
                                                     int radius) {
        RegionPoint best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = 0; dx <= radius; dx++) {
            for (int direction : dx == 0 ? new int[]{0} : new int[]{-1, 1}) {
                int sampleX = centerX + dx * direction;
                BotNavigationGraph.Region region = findRegionBelow(map, regionsById, regionIdByFootholdId, new Point(sampleX, probeY));
                if (region == null) {
                    continue;
                }

                Point point = region.pointAt(sampleX);
                int score = Math.abs(point.x - centerX) * 2 + Math.abs(point.y - probeY);
                if (score < bestScore) {
                    best = new RegionPoint(region, point);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static int scoreBottomRopeAccess(RopeAccess access, Rope rope) {
        return Math.abs(access.point.x - rope.x()) * 2 + Math.max(0, access.point.y - rope.bottomY());
    }

    private static int scoreTopRopeExit(RopeExit exit, Rope rope) {
        return Math.abs(exit.point.x - rope.x()) * 2
                + Math.abs(exit.point.y - rope.topY())
                + (exit.exitStepX == 0 ? 0 : 40);
    }

    private static int estimateClimbTraversalCost(Rope rope, Point start, Point end, int exitStepX) {
        Point ropeBottom = new Point(rope.x(), Math.min(Math.max(start.y, rope.topY()), rope.bottomY()));
        Point ropeTop = new Point(rope.x(), rope.topY());
        int entryCost = estimateWalkCost(start, ropeBottom);
        int climbCost = estimateClimbCost(ropeBottom, ropeTop);
        int exitCost = exitStepX == 0 ? estimateWalkCost(ropeTop, end) : estimateJumpCost(ropeTop, end);
        return entryCost + climbCost + exitCost;
    }

    private static void addPortalEdges(Portal portal,
                                       MapleMap map,
                                       Map<Integer, BotNavigationGraph.Region> regionsById,
                                       Map<Integer, Integer> regionIdByFootholdId,
                                       Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                       Set<String> edgeKeys) {
        if (portal.getTargetMapId() != map.getId()) {
            return;
        }

        Portal targetPortal = map.getPortal(portal.getTarget());
        if (targetPortal == null || targetPortal.getId() == portal.getId()) {
            return;
        }

        BotNavigationGraph.Region from = findRegionBelow(map, regionsById, regionIdByFootholdId, portal.getPosition());
        BotNavigationGraph.Region to = findRegionBelow(map, regionsById, regionIdByFootholdId, targetPortal.getPosition());
        if (from == null || to == null || from.id == to.id) {
            return;
        }

        Point start = from.pointAt(portal.getPosition().x);
        Point end = to.pointAt(targetPortal.getPosition().x);
        addEdge(from.id, to.id, BotNavigationGraph.EdgeType.PORTAL, start, end, 0, portal.getId(), 100, outgoing, edgeKeys);
    }

    private static Map<Integer, List<Integer>> buildFeatureXsByRegionId(MapleMap map,
                                                                        Map<Integer, Integer> regionIdByFootholdId) {
        Map<Integer, Set<Integer>> featureXs = new HashMap<>();

        for (Rope rope : map.getRopes()) {
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.bottomY() - 1)), rope.x());
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.topY() - 1)), rope.x());
        }

        for (Portal portal : map.getPortals()) {
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, portal.getPosition()), portal.getPosition().x);
            if (portal.getTargetMapId() != map.getId()) {
                continue;
            }

            Portal targetPortal = map.getPortal(portal.getTarget());
            if (targetPortal != null) {
                addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, targetPortal.getPosition()), targetPortal.getPosition().x);
            }
        }

        Map<Integer, List<Integer>> featuresByRegionId = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : featureXs.entrySet()) {
            List<Integer> xs = new ArrayList<>(entry.getValue());
            xs.sort(Integer::compareTo);
            featuresByRegionId.put(entry.getKey(), xs);
        }
        return featuresByRegionId;
    }

    private static void addFeatureX(Map<Integer, Set<Integer>> featureXs, int regionId, int x) {
        if (regionId < 0) {
            return;
        }
        featureXs.computeIfAbsent(regionId, ignored -> new HashSet<>()).add(x);
    }

    private static int findRegionIdBelow(MapleMap map,
                                         Map<Integer, Integer> regionIdByFootholdId,
                                         Point point) {
        if (map.getFootholds() == null) {
            return -1;
        }

        Foothold foothold = map.getFootholds().findBelow(point);
        if (foothold == null) {
            return -1;
        }

        return regionIdByFootholdId.getOrDefault(foothold.getId(), -1);
    }

    private static BotNavigationGraph.Region findRegionBelow(MapleMap map,
                                                             Map<Integer, BotNavigationGraph.Region> regionsById,
                                                             Map<Integer, Integer> regionIdByFootholdId,
                                                             Point point) {
        int regionId = findRegionIdBelow(map, regionIdByFootholdId, point);
        if (regionId < 0) {
            return null;
        }
        return regionsById.get(regionId);
    }

    private static List<Point> anchorPoints(BotNavigationGraph.Region region, List<Integer> featureXs) {
        List<Point> points = new ArrayList<>();
        addAnchor(points, region.leftPoint());
        for (BotNavigationGraph.Segment segment : region.segments) {
            addAnchor(points, new Point(segment.x1, segment.y1), ENDPOINT_ANCHOR_SPACING_PX);
            addAnchor(points, new Point(segment.x2, segment.y2), ENDPOINT_ANCHOR_SPACING_PX);
        }
        if (region.width() >= Math.max(BotMovementManager.cfg.FOLLOW_DIST * 2, 140)) {
            addAnchor(points, region.centerPoint());
        }
        if (region.width() >= Math.max(BotMovementManager.cfg.FOLLOW_DIST * 4, 260)) {
            addAnchor(points, region.pointAt(region.minX + region.width() / 3));
            addAnchor(points, region.pointAt(region.maxX - region.width() / 3));
        }
        addAnchor(points, region.rightPoint());
        for (int featureX : featureXs) {
            if (featureX >= region.minX && featureX <= region.maxX) {
                addAnchor(points, region.pointAt(featureX));
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.x).thenComparingInt(point -> point.y));
        return points;
    }

    private static void addAnchor(List<Point> points, Point point) {
        addAnchor(points, point, 0);
    }

    private static void addAnchor(List<Point> points, Point point, int minSpacingPx) {
        for (Point existing : points) {
            if (existing.equals(point)) {
                return;
            }
            if (minSpacingPx > 0
                    && Math.abs(existing.x - point.x) <= minSpacingPx
                    && Math.abs(existing.y - point.y) <= 8) {
                return;
            }
        }
        points.add(point);
    }

    private static boolean isWalkConnection(EndpointConnection connection) {
        int dx = Math.abs(connection.to.x - connection.from.x);
        int dy = connection.to.y - connection.from.y;
        return dx <= WALK_CONNECTION_GAP_PX
                && dy <= BotMovementManager.cfg.MAX_SNAP_DROP
                && dy >= -BotMovementManager.cfg.MAX_SLOPE_UP;
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                BotNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchStepX,
                                int portalId,
                                int cost,
                                Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        addEdge(fromRegionId, toRegionId, type, startPoint, endPoint, launchStepX, portalId,
                0, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                BotNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchStepX,
                                int portalId,
                                int ropeX,
                                int ropeTopY,
                                int ropeBottomY,
                                int cost,
                                Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        String key = fromRegionId + ":" + toRegionId + ":" + type + ":" + startPoint.x + ":" + startPoint.y + ":"
                + endPoint.x + ":" + endPoint.y + ":" + launchStepX + ":" + portalId + ":"
                + ropeX + ":" + ropeTopY + ":" + ropeBottomY;
        if (!edgeKeys.add(key)) {
            return;
        }

        outgoing.computeIfAbsent(fromRegionId, ignored -> new ArrayList<>())
                .add(new BotNavigationGraph.Edge(fromRegionId, toRegionId, type, startPoint, endPoint,
                        launchStepX, portalId, ropeX, ropeTopY, ropeBottomY, cost));
    }

    private static EndpointConnection closestEndpointConnection(Foothold first, Foothold second) {
        Point[] firstEndpoints = new Point[]{
                new Point(first.getX1(), first.getY1()),
                new Point(first.getX2(), first.getY2())
        };
        Point[] secondEndpoints = new Point[]{
                new Point(second.getX1(), second.getY1()),
                new Point(second.getX2(), second.getY2())
        };

        EndpointConnection best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Point from : firstEndpoints) {
            for (Point to : secondEndpoints) {
                int distance = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
                if (distance < bestDistance) {
                    best = new EndpointConnection(from, to);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static EndpointConnection sharedEndpointConnection(Foothold first, Foothold second) {
        Point[] firstEndpoints = new Point[]{
                new Point(first.getX1(), first.getY1()),
                new Point(first.getX2(), first.getY2())
        };
        Point[] secondEndpoints = new Point[]{
                new Point(second.getX1(), second.getY1()),
                new Point(second.getX2(), second.getY2())
        };

        for (Point from : firstEndpoints) {
            for (Point to : secondEndpoints) {
                if (from.equals(to)) {
                    return new EndpointConnection(from, to);
                }
            }
        }
        return null;
    }

    private static boolean hasCompatiblePlatformShape(Foothold first, Foothold second, Point sharedPoint) {
        if (first.isWall() || second.isWall() || sharedPoint == null) {
            return false;
        }

        Point firstOther = otherEndpoint(first, sharedPoint);
        Point secondOther = otherEndpoint(second, sharedPoint);
        if (firstOther == null || secondOther == null) {
            return false;
        }

        double firstDx = sharedPoint.x - firstOther.x;
        double firstDy = sharedPoint.y - firstOther.y;
        double secondDx = secondOther.x - sharedPoint.x;
        double secondDy = secondOther.y - sharedPoint.y;
        double firstLength = Math.hypot(firstDx, firstDy);
        double secondLength = Math.hypot(secondDx, secondDy);
        if (firstLength == 0.0 || secondLength == 0.0) {
            return false;
        }

        double continuationCosine = ((firstDx * secondDx) + (firstDy * secondDy)) / (firstLength * secondLength);
        return continuationCosine >= REGION_MERGE_MIN_CONTINUATION_COSINE;
    }

    private static Point otherEndpoint(Foothold foothold, Point sharedPoint) {
        Point first = new Point(foothold.getX1(), foothold.getY1());
        Point second = new Point(foothold.getX2(), foothold.getY2());
        if (first.equals(sharedPoint)) {
            return second;
        }
        if (second.equals(sharedPoint)) {
            return first;
        }
        return null;
    }

    private static int footholdMinX(Foothold foothold) {
        return Math.min(foothold.getX1(), foothold.getX2());
    }

    private static int groupMinX(List<Foothold> footholds) {
        int minX = Integer.MAX_VALUE;
        for (Foothold foothold : footholds) {
            minX = Math.min(minX, footholdMinX(foothold));
        }
        return minX;
    }

    private static int groupMinY(List<Foothold> footholds) {
        int minY = Integer.MAX_VALUE;
        for (Foothold foothold : footholds) {
            minY = Math.min(minY, Math.min(foothold.getY1(), foothold.getY2()));
        }
        return minY;
    }

    private static int estimateWalkCost(Point start, Point end) {
        return Math.max(50, (int) Math.round((Math.abs(end.x - start.x) * 1000.0) / Math.max(1, BotMovementManager.cfg.WALK_VEL)));
    }

    private static int estimateJumpCost(Point start, Point end) {
        int travel = Math.abs(end.x - start.x) + Math.abs(end.y - start.y);
        return 300 + Math.max(100, (int) Math.round((travel * 1000.0) / Math.max(1, BotMovementManager.cfg.WALK_VEL)));
    }

    private static int estimateDropCost(Point start, Point end) {
        return 200 + Math.max(100, (int) Math.round((Math.abs(end.y - start.y) * 1000.0) / Math.max(1, BotMovementManager.cfg.MAX_FALL_PXS)));
    }

    private static int dropLaunchStep(BotNavigationGraph.Region region, MapleMap map, Point anchor) {
        Point left = region.leftPoint();
        if (Math.abs(anchor.x - left.x) <= ENDPOINT_ANCHOR_SPACING_PX && Math.abs(anchor.y - left.y) <= 12) {
            return -BotMovementManager.walkStep(map);
        }

        Point right = region.rightPoint();
        if (Math.abs(anchor.x - right.x) <= ENDPOINT_ANCHOR_SPACING_PX && Math.abs(anchor.y - right.y) <= 12) {
            return BotMovementManager.walkStep(map);
        }

        return 0;
    }

    private static int estimateClimbCost(Point start, Point end) {
        return 150 + Math.max(100, (int) Math.round((Math.abs(end.y - start.y) * 1000.0) / Math.max(1, BotMovementManager.cfg.CLIMB_SPEED_PXS)));
    }

    private record EndpointConnection(Point from, Point to) {
    }

    private record RegionPoint(BotNavigationGraph.Region region, Point point) {
    }

    private record RopeAccess(int regionId, Point point) {
    }

    private record RopeExit(int regionId, Point point, int exitStepX) {
    }

    private static final class UnionFind {
        private final Map<Integer, Integer> parent = new HashMap<>();
        private final Map<Integer, Integer> rank = new HashMap<>();

        void add(int value) {
            parent.putIfAbsent(value, value);
            rank.putIfAbsent(value, 0);
        }

        int find(int value) {
            Integer parentValue = parent.get(value);
            if (parentValue == null) {
                add(value);
                return value;
            }
            if (parentValue == value) {
                return value;
            }

            int root = find(parentValue);
            parent.put(value, root);
            return root;
        }

        void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot == secondRoot) {
                return;
            }

            int firstRank = rank.getOrDefault(firstRoot, 0);
            int secondRank = rank.getOrDefault(secondRoot, 0);
            if (firstRank < secondRank) {
                parent.put(firstRoot, secondRoot);
                return;
            }
            if (firstRank > secondRank) {
                parent.put(secondRoot, firstRoot);
                return;
            }

            parent.put(secondRoot, firstRoot);
            rank.put(firstRoot, firstRank + 1);
        }
    }
}
