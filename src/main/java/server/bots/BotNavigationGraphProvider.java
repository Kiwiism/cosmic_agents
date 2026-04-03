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

    private static final int GRAPH_VERSION = 17;
    private static final int ENDPOINT_ANCHOR_SPACING_PX = 10;
    private static final int ROPE_ANCHOR_INTERVAL_PX = 30;
    private static final double REGION_MERGE_MIN_CONTINUATION_COSINE = 0.5; // 1 = straight, 0 = 90degree, negative = u-turn
    private static final Path CACHE_DIR = Path.of("cache", "bot-nav", "v" + GRAPH_VERSION);
    private static final Map<Integer, BotNavigationGraph> GRAPHS = new ConcurrentHashMap<>();
    private static final Map<Integer, GraphBuildReport> LAST_BUILD_REPORTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<BuildProfileBuilder> ACTIVE_BUILD_PROFILE = new ThreadLocal<>();

    static final class GraphBuildReport {
        final int mapId;
        final int footholdCount;
        final int walkableFootholdCount;
        final int ropeCount;
        final int regionCount;
        final int totalEdgeCount;
        final int walkEdgeCount;
        final int jumpEdgeCount;
        final int dropEdgeCount;
        final int climbEdgeCount;
        final int portalEdgeCount;
        final long collectFootholdsNs;
        final long buildRegionsNs;
        final long addRopeRegionsNs;
        final long buildFeatureXsNs;
        final long buildWalkEdgesNs;
        final long buildDropEdgesNs;
        final long buildJumpEdgesNs;
        final long buildRopeEntryEdgesNs;
        final long buildRopeExitEdgesNs;
        final long buildPortalEdgesNs;
        final long totalBuildNs;
        final long jumpWindowProbeCount;
        final long jumpWindowBoundarySearchCount;

        GraphBuildReport(int mapId,
                         int footholdCount,
                         int walkableFootholdCount,
                         int ropeCount,
                         int regionCount,
                         int totalEdgeCount,
                         int walkEdgeCount,
                         int jumpEdgeCount,
                         int dropEdgeCount,
                         int climbEdgeCount,
                         int portalEdgeCount,
                         long collectFootholdsNs,
                         long buildRegionsNs,
                         long addRopeRegionsNs,
                         long buildFeatureXsNs,
                         long buildWalkEdgesNs,
                         long buildDropEdgesNs,
                         long buildJumpEdgesNs,
                         long buildRopeEntryEdgesNs,
                         long buildRopeExitEdgesNs,
                         long buildPortalEdgesNs,
                         long totalBuildNs,
                         long jumpWindowProbeCount,
                         long jumpWindowBoundarySearchCount) {
            this.mapId = mapId;
            this.footholdCount = footholdCount;
            this.walkableFootholdCount = walkableFootholdCount;
            this.ropeCount = ropeCount;
            this.regionCount = regionCount;
            this.totalEdgeCount = totalEdgeCount;
            this.walkEdgeCount = walkEdgeCount;
            this.jumpEdgeCount = jumpEdgeCount;
            this.dropEdgeCount = dropEdgeCount;
            this.climbEdgeCount = climbEdgeCount;
            this.portalEdgeCount = portalEdgeCount;
            this.collectFootholdsNs = collectFootholdsNs;
            this.buildRegionsNs = buildRegionsNs;
            this.addRopeRegionsNs = addRopeRegionsNs;
            this.buildFeatureXsNs = buildFeatureXsNs;
            this.buildWalkEdgesNs = buildWalkEdgesNs;
            this.buildDropEdgesNs = buildDropEdgesNs;
            this.buildJumpEdgesNs = buildJumpEdgesNs;
            this.buildRopeEntryEdgesNs = buildRopeEntryEdgesNs;
            this.buildRopeExitEdgesNs = buildRopeExitEdgesNs;
            this.buildPortalEdgesNs = buildPortalEdgesNs;
            this.totalBuildNs = totalBuildNs;
            this.jumpWindowProbeCount = jumpWindowProbeCount;
            this.jumpWindowBoundarySearchCount = jumpWindowBoundarySearchCount;
        }
    }

    private static final class BuildProfileBuilder {
        private final int mapId;
        private final long buildStartedAtNs = System.nanoTime();
        private int footholdCount;
        private int walkableFootholdCount;
        private int ropeCount;
        private int regionCount;
        private int totalEdgeCount;
        private int walkEdgeCount;
        private int jumpEdgeCount;
        private int dropEdgeCount;
        private int climbEdgeCount;
        private int portalEdgeCount;
        private long collectFootholdsNs;
        private long buildRegionsNs;
        private long addRopeRegionsNs;
        private long buildFeatureXsNs;
        private long buildWalkEdgesNs;
        private long buildDropEdgesNs;
        private long buildJumpEdgesNs;
        private long buildRopeEntryEdgesNs;
        private long buildRopeExitEdgesNs;
        private long buildPortalEdgesNs;
        private long jumpWindowProbeCount;
        private long jumpWindowBoundarySearchCount;

        private BuildProfileBuilder(int mapId) {
            this.mapId = mapId;
        }

        private void recordEdge(BotNavigationGraph.EdgeType type) {
            totalEdgeCount++;
            switch (type) {
                case WALK -> walkEdgeCount++;
                case JUMP -> jumpEdgeCount++;
                case DROP -> dropEdgeCount++;
                case CLIMB -> climbEdgeCount++;
                case PORTAL -> portalEdgeCount++;
            }
        }

        private GraphBuildReport finish() {
            return new GraphBuildReport(
                    mapId,
                    footholdCount,
                    walkableFootholdCount,
                    ropeCount,
                    regionCount,
                    totalEdgeCount,
                    walkEdgeCount,
                    jumpEdgeCount,
                    dropEdgeCount,
                    climbEdgeCount,
                    portalEdgeCount,
                    collectFootholdsNs,
                    buildRegionsNs,
                    addRopeRegionsNs,
                    buildFeatureXsNs,
                    buildWalkEdgesNs,
                    buildDropEdgesNs,
                    buildJumpEdgesNs,
                    buildRopeEntryEdgesNs,
                    buildRopeExitEdgesNs,
                    buildPortalEdgesNs,
                    System.nanoTime() - buildStartedAtNs,
                    jumpWindowProbeCount,
                    jumpWindowBoundarySearchCount);
        }
    }

    private record JumpLaunchWindow(int minX, int maxX, Point startPoint, Point endPoint) {
    }

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
        BuildProfileBuilder profile = new BuildProfileBuilder(map.getId());
        ACTIVE_BUILD_PROFILE.set(profile);
        try {
            List<Foothold> footholds = map.getFootholds() == null ? List.of() : map.getFootholds().getAllFootholds();
            Map<Integer, Foothold> footholdsById = new HashMap<>();
            List<Foothold> walkableFootholds = new ArrayList<>();
            long phaseStartedAt = System.nanoTime();
            for (Foothold foothold : footholds) {
                footholdsById.put(foothold.getId(), foothold);
                if (!foothold.isWall()) {
                    walkableFootholds.add(foothold);
                }
            }
            profile.collectFootholdsNs = System.nanoTime() - phaseStartedAt;
            profile.footholdCount = footholds.size();
            profile.walkableFootholdCount = walkableFootholds.size();
            profile.ropeCount = map.getRopes().size();

            List<BotNavigationGraph.Region> regions = new ArrayList<>();
            Map<Integer, BotNavigationGraph.Region> regionsById = new HashMap<>();
            Map<Integer, Integer> regionIdByFootholdId = new HashMap<>();
            phaseStartedAt = System.nanoTime();
            buildRegions(walkableFootholds, footholdsById, regions, regionsById, regionIdByFootholdId);
            profile.buildRegionsNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            int nextRegionId = regions.stream().mapToInt(r -> r.id).max().orElse(0) + 1;
            for (Rope rope : map.getRopes()) {
                BotNavigationGraph.Region ropeRegion = new BotNavigationGraph.Region(
                        nextRegionId++, rope.x(), rope.topY(), rope.bottomY(), rope.isLadder());
                regions.add(ropeRegion);
                regionsById.put(ropeRegion.id, ropeRegion);
            }
            profile.addRopeRegionsNs = System.nanoTime() - phaseStartedAt;
            profile.regionCount = regions.size();

            phaseStartedAt = System.nanoTime();
            Map<Integer, List<Integer>> featureXsByRegionId = buildFeatureXsByRegionId(map, regions, regionIdByFootholdId);
            profile.buildFeatureXsNs = System.nanoTime() - phaseStartedAt;
            Map<Integer, List<BotNavigationGraph.Edge>> outgoing = new HashMap<>();
            Set<String> edgeKeys = new HashSet<>();

            phaseStartedAt = System.nanoTime();
            for (Foothold foothold : walkableFootholds) {
                addWalkEdges(foothold, footholdsById, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
            }
            profile.buildWalkEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (BotNavigationGraph.Region region : regions) {
                if (region.isRopeRegion) {
                    continue;
                }
                addDropEdges(region, map, regionsById, regionIdByFootholdId, featureXsByRegionId, outgoing, edgeKeys);
            }
            profile.buildDropEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (BotNavigationGraph.Region region : regions) {
                if (region.isRopeRegion) {
                    continue;
                }
                addJumpEdges(region, map, regionsById, regionIdByFootholdId, featureXsByRegionId, outgoing, edgeKeys);
            }
            profile.buildJumpEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (BotNavigationGraph.Region region : regions) {
                if (!region.isRopeRegion) {
                    continue;
                }
                addRopeEntryEdges(region, map, regionsById, featureXsByRegionId, outgoing, edgeKeys);
            }
            profile.buildRopeEntryEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (BotNavigationGraph.Region region : regions) {
                if (!region.isRopeRegion) {
                    continue;
                }
                addRopeExitEdges(region, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
            }
            profile.buildRopeExitEdgesNs = System.nanoTime() - phaseStartedAt;

            phaseStartedAt = System.nanoTime();
            for (Portal portal : map.getPortals()) {
                addPortalEdges(portal, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);
            }
            profile.buildPortalEdgesNs = System.nanoTime() - phaseStartedAt;

            BotNavigationGraph graph = new BotNavigationGraph(map.getId(), GRAPH_VERSION, regions, regionsById, regionIdByFootholdId, outgoing);
            GraphBuildReport report = profile.finish();
            LAST_BUILD_REPORTS.put(map.getId(), report);
            log.debug("Built bot nav graph map {} in {} ms (regions={}, edges={}, jump={} ms, probes={})",
                    map.getId(),
                    String.format("%.2f", report.totalBuildNs / 1_000_000.0),
                    report.regionCount,
                    report.totalEdgeCount,
                    String.format("%.2f", report.buildJumpEdgesNs / 1_000_000.0),
                    report.jumpWindowProbeCount);
            return graph;
        } finally {
            ACTIVE_BUILD_PROFILE.remove();
        }
    }

    static GraphBuildReport getLastBuildReport(int mapId) {
        return LAST_BUILD_REPORTS.get(mapId);
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
        if (!BotPhysicsEngine.canWalkAcrossFootholds(first, second)) {
            return;
        }
        unionFind.union(first.getId(), second.getId());
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

            int dropCost = estimateDropCost(anchor, landing.point()) + (dropStepX == 0 ? 100 : 0);
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
        int jumpStep = BotPhysicsEngine.walkStep(map);
        for (Point anchor : anchorPoints(from, featureXsByRegionId.getOrDefault(from.id, List.of()))) {
            for (int launchStepX : new int[]{-jumpStep, 0, jumpStep}) {
                BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(map, anchor, launchStepX);
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

                JumpLaunchWindow launchWindow = expandJumpLaunchWindow(from, map, regionIdByFootholdId,
                        anchor.x, launchStepX, to.id);
                if (launchWindow == null) {
                    continue;
                }

                addEdge(from.id, to.id, BotNavigationGraph.EdgeType.JUMP,
                        launchWindow.startPoint(), launchWindow.endPoint(),
                        launchWindow.minX(), launchWindow.maxX(),
                        launchStepX, 0, estimateJumpCost(launchWindow.startPoint(), launchWindow.endPoint()),
                        outgoing, edgeKeys);
            }
        }
    }

    private static JumpLaunchWindow expandJumpLaunchWindow(BotNavigationGraph.Region from,
                                                           MapleMap map,
                                                           Map<Integer, Integer> regionIdByFootholdId,
                                                           int anchorX,
                                                           int launchStepX,
                                                           int targetRegionId) {
        int minX = findJumpLaunchBoundary(from, map, regionIdByFootholdId, anchorX, launchStepX, targetRegionId, true);
        int maxX = findJumpLaunchBoundary(from, map, regionIdByFootholdId, anchorX, launchStepX, targetRegionId, false);

        int representativeX = (minX + maxX) / 2;
        Point representativeStart = from.pointAt(representativeX);
        BotPhysicsEngine.JumpLanding representativeLanding =
                BotPhysicsEngine.simulateJumpLanding(map, representativeStart, launchStepX);
        if (representativeLanding == null) {
            return null;
        }

        int landingRegionId = regionIdByFootholdId.getOrDefault(representativeLanding.foothold().getId(), -1);
        if (landingRegionId != targetRegionId) {
            return null;
        }

        return new JumpLaunchWindow(minX, maxX, representativeStart, representativeLanding.point());
    }

    private static int findJumpLaunchBoundary(BotNavigationGraph.Region from,
                                              MapleMap map,
                                              Map<Integer, Integer> regionIdByFootholdId,
                                              int anchorX,
                                              int launchStepX,
                                              int targetRegionId,
                                              boolean searchLeft) {
        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.jumpWindowBoundarySearchCount++;
        }
        int validX = anchorX;
        int invalidX = anchorX;
        int step = 1;

        while (true) {
            int probeX = searchLeft
                    ? Math.max(from.minX, anchorX - step)
                    : Math.min(from.maxX, anchorX + step);
            if (probeX == validX) {
                break;
            }

            if (!landsJumpInRegion(map, regionIdByFootholdId, from.pointAt(probeX), launchStepX, targetRegionId)) {
                invalidX = probeX;
                break;
            }

            validX = probeX;
            if (probeX == (searchLeft ? from.minX : from.maxX)) {
                return probeX;
            }
            step *= 2;
        }

        while (Math.abs(validX - invalidX) > 1) {
            int probeX = (validX + invalidX) / 2;
            boolean valid = landsJumpInRegion(map, regionIdByFootholdId, from.pointAt(probeX), launchStepX, targetRegionId);
            if (valid) {
                validX = probeX;
            } else {
                invalidX = probeX;
            }
        }
        return validX;
    }

    private static boolean landsJumpInRegion(MapleMap map,
                                             Map<Integer, Integer> regionIdByFootholdId,
                                             Point start,
                                             int launchStepX,
                                             int targetRegionId) {
        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.jumpWindowProbeCount++;
        }
        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(map, start, launchStepX);
        if (landing == null) {
            return false;
        }
        return regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1) == targetRegionId;
    }

    // --- Rope entry edges: ground/rope region → rope region ---

    private static void addRopeEntryEdges(BotNavigationGraph.Region ropeRegion,
                                          MapleMap map,
                                          Map<Integer, BotNavigationGraph.Region> regionsById,
                                          Map<Integer, List<Integer>> featureXsByRegionId,
                                          Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                          Set<String> edgeKeys) {
        Rope rope = findRopeFromRegion(map, ropeRegion);
        if (rope == null) {
            return;
        }

        int ropeX = rope.x();
        for (BotNavigationGraph.Region ground : regionsById.values()) {
            if (ground.isRopeRegion) {
                continue;
            }

            Point bestEntry = null;
            int bestScore = Integer.MAX_VALUE;
            for (Point anchor : anchorPoints(ground, featureXsByRegionId.getOrDefault(ground.id, List.of()))) {
                boolean canGrab = Math.abs(anchor.x - ropeX) <= BotMovementManager.cfg.ROPE_GRAB_X
                        && anchor.y >= rope.topY() && anchor.y <= rope.bottomY();
                boolean canJumpGrab = BotMovementManager.canReachRopeFromGround(map, anchor, rope);
                boolean canTopStep = anchor.y <= rope.topY() + BotMovementManager.cfg.JUMP_Y_THRESH
                        && Math.abs(anchor.x - ropeX) <= BotMovementManager.cfg.ROPE_GRAB_X;

                if (canGrab || canJumpGrab || canTopStep) {
                    int score = Math.abs(anchor.x - ropeX) * 2 + Math.abs(anchor.y - rope.bottomY());
                    if (score < bestScore) {
                        bestScore = score;
                        bestEntry = anchor;
                    }
                }
            }

            if (bestEntry != null) {
                int entryY = Math.max(rope.topY(), Math.min(bestEntry.y, rope.bottomY()));
                Point ropePoint = new Point(ropeX, entryY);
                int cost = estimateWalkCost(bestEntry, new Point(ropeX, bestEntry.y)) + 200;
                addEdge(ground.id, ropeRegion.id, BotNavigationGraph.EdgeType.CLIMB,
                        bestEntry, ropePoint, 0, 0, cost, outgoing, edgeKeys);
            }
        }
    }

    // --- Rope exit edges: rope region → ground/rope region ---

    private static void addRopeExitEdges(BotNavigationGraph.Region ropeRegion,
                                         MapleMap map,
                                         Map<Integer, BotNavigationGraph.Region> regionsById,
                                         Map<Integer, Integer> regionIdByFootholdId,
                                         Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                         Set<String> edgeKeys) {
        Rope rope = findRopeFromRegion(map, ropeRegion);
        if (rope == null) {
            return;
        }

        int ropeX = rope.x();
        int jumpStep = BotMovementManager.walkStep(map);

        // Direct step-off at the top of the rope
        addTopStepOffEdge(ropeRegion, rope, map, regionsById, regionIdByFootholdId, outgoing, edgeKeys);

        // Jump-off / step-off to ground at various heights along the rope
        for (int anchorY : ropeAnchorYs(rope)) {
            Point ropePoint = new Point(ropeX, anchorY);
            for (int stepX : new int[]{-jumpStep, 0, jumpStep}) {
                BotMovementManager.JumpLanding landing = BotMovementManager.simulateRopeJumpLanding(map, ropePoint, stepX);
                if (landing == null) {
                    continue;
                }

                int toRegionId = regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
                BotNavigationGraph.Region toRegion = regionsById.get(toRegionId);
                if (toRegion == null || toRegion.isRopeRegion) {
                    continue;
                }

                int cost = estimateRopeExitJumpCost();
                addEdge(ropeRegion.id, toRegion.id, BotNavigationGraph.EdgeType.CLIMB,
                        ropePoint, landing.point(), stepX, 0, cost, outgoing, edgeKeys);
            }
        }

        // Rope-to-rope transfers need a tighter vertical sweep than generic rope exits.
        for (int anchorY : ropeTransferAnchorYs(rope)) {
            Point ropePoint = new Point(ropeX, anchorY);
            for (BotNavigationGraph.Region otherRope : regionsById.values()) {
                if (!otherRope.isRopeRegion || otherRope.id == ropeRegion.id) {
                    continue;
                }

                Rope targetRope = findRopeFromRegion(map, otherRope);
                if (targetRope == null) {
                    continue;
                }

                int dx = Math.abs(ropeX - targetRope.x());
                if (dx > BotPhysicsEngine.maxRopeJumpHorizontalTravel(map)) {
                    continue;
                }

                int launchDir = targetRope.x() > ropeX ? jumpStep : -jumpStep;
                Point ropeGrab = BotPhysicsEngine.simulateRopeJumpGrab(map, ropePoint, launchDir, targetRope);
                if (ropeGrab == null) {
                    continue;
                }

                int cost = estimateRopeExitJumpCost() + 150;
                addEdge(ropeRegion.id, otherRope.id, BotNavigationGraph.EdgeType.CLIMB,
                        ropePoint, ropeGrab, launchDir, 0, cost, outgoing, edgeKeys);
            }
        }
    }

    private static void addTopStepOffEdge(BotNavigationGraph.Region ropeRegion,
                                          Rope rope,
                                          MapleMap map,
                                          Map<Integer, BotNavigationGraph.Region> regionsById,
                                          Map<Integer, Integer> regionIdByFootholdId,
                                          Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                          Set<String> edgeKeys) {
        int radius = Math.max(BotMovementManager.cfg.ROPE_GRAB_X, 28);
        for (int dx = 0; dx <= radius; dx++) {
            for (int direction : dx == 0 ? new int[]{0} : new int[]{-1, 1}) {
                int sampleX = rope.x() + dx * direction;
                BotNavigationGraph.Region ground = findRegionBelow(map, regionsById, regionIdByFootholdId,
                        new Point(sampleX, rope.topY() - 1));
                if (ground == null || ground.isRopeRegion) {
                    continue;
                }

                Point landPoint = ground.pointAt(sampleX);
                if (Math.abs(landPoint.y - rope.topY()) > BotMovementManager.cfg.JUMP_Y_THRESH * 2) {
                    continue;
                }

                Point ropePoint = new Point(rope.x(), rope.topY());
                int cost = estimateWalkCost(ropePoint, landPoint);
                addEdge(ropeRegion.id, ground.id, BotNavigationGraph.EdgeType.CLIMB,
                        ropePoint, landPoint, 0, 0, cost, outgoing, edgeKeys);
                return;
            }
        }
    }

    private static List<Integer> ropeAnchorYs(Rope rope) {
        List<Integer> ys = new ArrayList<>();
        ys.add(rope.topY());
        for (int y = rope.topY() + ROPE_ANCHOR_INTERVAL_PX; y < rope.bottomY(); y += ROPE_ANCHOR_INTERVAL_PX) {
            ys.add(y);
        }
        ys.add(rope.bottomY());
        return ys;
    }

    private static List<Integer> ropeTransferAnchorYs(Rope rope) {
        List<Integer> ys = new ArrayList<>();
        int step = Math.max(1, BotPhysicsEngine.climbStepPerTick());
        for (int y = rope.topY(); y <= rope.bottomY(); y += step) {
            ys.add(y);
        }
        if (ys.isEmpty() || ys.getLast() != rope.bottomY()) {
            ys.add(rope.bottomY());
        }
        return ys;
    }

    static Rope findRopeFromRegion(MapleMap map, BotNavigationGraph.Region ropeRegion) {
        if (ropeRegion == null || !ropeRegion.isRopeRegion) {
            return null;
        }
        for (Rope rope : map.getRopes()) {
            if (rope.x() == ropeRegion.minX && rope.topY() == ropeRegion.minY && rope.bottomY() == ropeRegion.maxY) {
                return rope;
            }
        }
        return null;
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

        // Portal WZ positions can sit a few pixels below the foothold surface they belong to,
        // causing findBelow to skip the correct foothold and land on the next lower platform.
        // Probe MAX_SNAP_DROP above the portal position so findBelow always finds the right foothold.
        int snapUp = BotPhysicsEngine.cfg.MAX_SNAP_DROP;
        BotNavigationGraph.Region from = findRegionBelow(map, regionsById, regionIdByFootholdId,
                new Point(portal.getPosition().x, portal.getPosition().y - snapUp));
        BotNavigationGraph.Region to = findRegionBelow(map, regionsById, regionIdByFootholdId,
                new Point(targetPortal.getPosition().x, targetPortal.getPosition().y - snapUp));
        if (from == null || to == null || from.id == to.id) {
            return;
        }

        Point start = from.pointAt(portal.getPosition().x);
        Point end = to.pointAt(targetPortal.getPosition().x);
        // Portals are instant teleports — cost is effectively 0 travel time.
        addEdge(from.id, to.id, BotNavigationGraph.EdgeType.PORTAL, start, end, 0, portal.getId(), 1, outgoing, edgeKeys);
    }

    private static Map<Integer, List<Integer>> buildFeatureXsByRegionId(MapleMap map,
                                                                        List<BotNavigationGraph.Region> regions,
                                                                        Map<Integer, Integer> regionIdByFootholdId) {
        Map<Integer, Set<Integer>> featureXs = new HashMap<>();

        for (Rope rope : map.getRopes()) {
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.bottomY() - 1)), rope.x());
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(rope.x(), rope.topY() - 1)), rope.x());
        }

        for (Portal portal : map.getPortals()) {
            int snapUp = BotPhysicsEngine.cfg.MAX_SNAP_DROP;
            Point portalProbe = new Point(portal.getPosition().x, portal.getPosition().y - snapUp);
            addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, portalProbe), portal.getPosition().x);
            if (portal.getTargetMapId() != map.getId()) {
                continue;
            }

            Portal targetPortal = map.getPortal(portal.getTarget());
            if (targetPortal != null) {
                Point targetProbe = new Point(targetPortal.getPosition().x, targetPortal.getPosition().y - snapUp);
                addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, targetProbe), targetPortal.getPosition().x);
            }
        }

        for (BotNavigationGraph.Region region : regions) {
            if (region.isRopeRegion || region.width() > 64) {
                continue;
            }
            projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.leftPoint());
            projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.centerPoint());
            projectRegionXsToRegionBelow(featureXs, map, regionIdByFootholdId, region.rightPoint());
        }

        Map<Integer, List<Integer>> featuresByRegionId = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : featureXs.entrySet()) {
            List<Integer> xs = new ArrayList<>(entry.getValue());
            xs.sort(Integer::compareTo);
            featuresByRegionId.put(entry.getKey(), xs);
        }
        return featuresByRegionId;
    }

    private static void projectRegionXsToRegionBelow(Map<Integer, Set<Integer>> featureXs,
                                                     MapleMap map,
                                                     Map<Integer, Integer> regionIdByFootholdId,
                                                     Point point) {
        if (point == null) {
            return;
        }
        addFeatureX(featureXs, findRegionIdBelow(map, regionIdByFootholdId, new Point(point.x, point.y + 1)), point.x);
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
        // Near-edge anchors for better jump/drop accuracy at platform boundaries
        int edgeInset = Math.max(8, (int) Math.round(BotMovementManager.cfg.WALK_VEL * BotPhysicsEngine.cfg.TICK_MS / 1000.0));
        if (region.width() > edgeInset * 2) {
            addAnchor(points, region.pointAt(region.minX + edgeInset), ENDPOINT_ANCHOR_SPACING_PX);
            addAnchor(points, region.pointAt(region.maxX - edgeInset), ENDPOINT_ANCHOR_SPACING_PX);
        }
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
        return BotPhysicsEngine.isWalkableEndpointStep(dx, dy);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                BotNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchMinX,
                                int launchMaxX,
                                int launchStepX,
                                int portalId,
                                int cost,
                                Map<Integer, List<BotNavigationGraph.Edge>> outgoing,
                                Set<String> edgeKeys) {
        addEdge(fromRegionId, toRegionId, type, startPoint, endPoint, launchMinX, launchMaxX, launchStepX, portalId,
                0, 0, 0, cost, outgoing, edgeKeys);
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
        addEdge(fromRegionId, toRegionId, type, startPoint, endPoint, startPoint.x, startPoint.x, launchStepX, portalId,
                0, 0, 0, cost, outgoing, edgeKeys);
    }

    private static void addEdge(int fromRegionId,
                                int toRegionId,
                                BotNavigationGraph.EdgeType type,
                                Point startPoint,
                                Point endPoint,
                                int launchMinX,
                                int launchMaxX,
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
                + ropeX + ":" + ropeTopY + ":" + ropeBottomY + ":" + launchMinX + ":" + launchMaxX;
        if (!edgeKeys.add(key)) {
            return;
        }

        outgoing.computeIfAbsent(fromRegionId, ignored -> new ArrayList<>())
                .add(new BotNavigationGraph.Edge(fromRegionId, toRegionId, type, startPoint, endPoint,
                        launchMinX, launchMaxX, launchStepX, portalId, ropeX, ropeTopY, ropeBottomY, cost));
        BuildProfileBuilder profile = ACTIVE_BUILD_PROFILE.get();
        if (profile != null) {
            profile.recordEdge(type);
        }
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
        // Use real jump airtime from BotPhysicsEngine physics constants.
        // Not charging horizontal distance means diagonal jumps (launchStepX != 0) cost
        // the same as vertical jumps — A* naturally prefers them because the bot doesn't
        // also need to walk horizontally to align under the target first.
        return 300 + jumpAirtimeCostMs(BotPhysicsEngine.jumpForcePerTick());
    }

    private static int estimateRopeExitJumpCost() {
        return 300 + jumpAirtimeCostMs(BotPhysicsEngine.ropeJumpForcePerTick());
    }

    private static int jumpAirtimeCostMs(float launchForce) {
        float gravity = BotPhysicsEngine.gravityPerTick();
        int ticks = Math.max(1, (int) Math.ceil(2.0 * launchForce / gravity));
        return ticks * BotPhysicsEngine.cfg.TICK_MS;
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

    private record EndpointConnection(Point from, Point to) {
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
