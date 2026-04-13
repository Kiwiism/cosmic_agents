package server.bots;

import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BotNavigationProbe {
    private BotNavigationProbe() {
    }

    public static List<String> rebuildGraphReport(MapleMap map) {
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(map);
        return formatBuildReport(graph, BotNavigationGraphProvider.getLastBuildReport(map.getId(), graph.movementProfile));
    }

    public static List<String> lastBuildReport(MapleMap map) {
        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(map);
        return formatBuildReport(graph,
                BotNavigationGraphProvider.getLastBuildReport(map.getId(), graph.movementProfile));
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        int mapId = Integer.parseInt(args[0]);
        boolean rebuild = false;
        boolean listRopes = false;
        List<Point> points = new ArrayList<>();
        List<Point> jumps = new ArrayList<>();
        List<PathProbe> paths = new ArrayList<>();
        List<Integer> regions = new ArrayList<>();
        List<Integer> edgeRegions = new ArrayList<>();
        List<RegionPathProbe> regionPaths = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--rebuild" -> rebuild = true;
                case "--ropes" -> listRopes = true;
                case "--point" -> points.add(parsePoint(nextArg(args, ++i, "--point")));
                case "--jump" -> jumps.add(parsePoint(nextArg(args, ++i, "--jump")));
                case "--path" -> paths.add(parsePath(nextArg(args, ++i, "--path")));
                case "--region" -> regions.add(Integer.parseInt(nextArg(args, ++i, "--region")));
                case "--edges" -> edgeRegions.add(Integer.parseInt(nextArg(args, ++i, "--edges")));
                case "--path-region" -> regionPaths.add(parseRegionPath(nextArg(args, ++i, "--path-region")));
                default -> throw new IllegalArgumentException("Unknown arg: " + arg);
            }
        }

        MapleMap map = BotNavigationMapLoader.loadMapGeometry(mapId);
        BotNavigationGraph graph = rebuild
                ? BotNavigationGraphProvider.rebuildGraph(map)
                : BotNavigationGraphProvider.getGraph(map);

        printSummary(map, graph);
        if (rebuild) {
            for (String line : formatBuildReport(graph, BotNavigationGraphProvider.getLastBuildReport(map.getId(), graph.movementProfile))) {
                System.out.println(line);
            }
        }
        for (Point point : points) {
            probePoint(map, graph, point);
        }
        for (Point point : jumps) {
            probeJump(map, graph, point);
        }
        for (int regionId : regions) {
            probeRegion(graph, regionId);
        }
        for (int regionId : edgeRegions) {
            probeEdges(graph, regionId);
        }
        if (listRopes) {
            probeRopes(graph, map);
        }
        for (PathProbe path : paths) {
            probePath(map, graph, path);
        }
        for (RegionPathProbe regionPath : regionPaths) {
            probeRegionPath(graph, regionPath);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: BotNavigationProbe <mapId> [--rebuild] [--point x,y] [--jump x,y] [--path x1,y1:x2,y2]");
        System.out.println("       BotNavigationProbe <mapId> [--region id] [--edges id] [--path-region fromRegion:toRegion] [--ropes]");
        System.out.println("Example: BotNavigationProbe 100000000 --rebuild --point 1080,334 --jump 1080,334 --path 990,334:938,274");
    }

    private static String nextArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    private static Point parsePoint(String raw) {
        String[] parts = raw.split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected point as x,y but got: " + raw);
        }
        return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static PathProbe parsePath(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected path as x1,y1:x2,y2 but got: " + raw);
        }
        return new PathProbe(parsePoint(parts[0]), parsePoint(parts[1]));
    }

    private static RegionPathProbe parseRegionPath(String raw) {
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected region path as fromRegion:toRegion but got: " + raw);
        }
        return new RegionPathProbe(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static void printSummary(MapleMap map, BotNavigationGraph graph) {
        int jumpEdges = 0;
        int dropEdges = 0;
        int climbEdges = 0;
        int portalEdges = 0;
        int walkEdges = 0;
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                switch (edge.type) {
                    case WALK -> walkEdges++;
                    case JUMP -> jumpEdges++;
                    case DROP -> dropEdges++;
                    case CLIMB -> climbEdges++;
                    case PORTAL -> portalEdges++;
                    default -> {
                    }
                }
            }
        }

        System.out.printf("Map %d  speed=%d jump=%d walkStep=%d  regions=%d  edges walk=%d jump=%d drop=%d climb=%d portal=%d%n",
                map.getId(),
                graph.movementProfile.totalSpeedStat(),
                graph.movementProfile.totalJumpStat(),
                BotMovementManager.walkStep(map, graph.movementProfile),
                graph.regions.size(), walkEdges, jumpEdges, dropEdges, climbEdges, portalEdges);
    }

    private static List<String> formatBuildReport(BotNavigationGraph graph,
                                                  BotNavigationGraphProvider.GraphBuildReport report) {
        if (report == null) {
            return List.of("Bot nav build report unavailable.");
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                "Bot nav rebuild map=%d speed=%d jump=%d total=%.2fms regions=%d edges=%d footholds=%d walkable=%d ropes=%d",
                report.mapId,
                report.totalSpeedStat,
                report.totalJumpStat,
                nanosToMs(report.totalBuildNs),
                report.regionCount,
                report.totalEdgeCount,
                report.footholdCount,
                report.walkableFootholdCount,
                report.ropeCount));
        lines.add(String.format(
                "Phases: collect=%.2f regions=%.2f ropeRegions=%.2f features=%.2f anchors=%.2f walk=%.2f drop=%.2f jump=%.2f ropeIn=%.2f ropeOut=%.2f portal=%.2f ms",
                nanosToMs(report.collectFootholdsNs),
                nanosToMs(report.buildRegionsNs),
                nanosToMs(report.addRopeRegionsNs),
                nanosToMs(report.buildFeatureXsNs),
                nanosToMs(report.buildAnchorPointsNs),
                nanosToMs(report.buildWalkEdgesNs),
                nanosToMs(report.buildDropEdgesNs),
                nanosToMs(report.buildJumpEdgesNs),
                nanosToMs(report.buildRopeEntryEdgesNs),
                nanosToMs(report.buildRopeExitEdgesNs),
                nanosToMs(report.buildPortalEdgesNs)));
        lines.add(String.format(
                "Edges: walk=%d jump=%d drop=%d climb=%d portal=%d | jumpSamples=%d cacheHits=%d cacheMisses=%d refineProbes=%d",
                report.walkEdgeCount,
                report.jumpEdgeCount,
                report.dropEdgeCount,
                report.climbEdgeCount,
                report.portalEdgeCount,
                report.jumpSampleCount,
                report.jumpCacheHitCount,
                report.jumpCacheMissCount,
                report.jumpBoundaryRefineProbeCount));
        if (!report.slowestJumpRegions.isEmpty()) {
            StringBuilder slowRegions = new StringBuilder("Slow jump regions:");
            for (BotNavigationGraphProvider.JumpRegionProfile profile : report.slowestJumpRegions) {
                slowRegions.append(String.format(" r%d[w=%d samples=%d edges=%d hits=%d misses=%d time=%.2fms]",
                        profile.regionId(),
                        profile.width(),
                        profile.sampleCount(),
                        profile.edgeCount(),
                        profile.cacheHits(),
                        profile.cacheMisses(),
                        nanosToMs(profile.elapsedNs())));
            }
            lines.add(slowRegions.toString());
        }
        if (graph != null) {
            lines.add(String.format("Cache: version=%d map=%d speed=%d jump=%d regions=%d",
                    graph.version, graph.mapId, graph.movementProfile.totalSpeedStat(), graph.movementProfile.totalJumpStat(), graph.regions.size()));
        }
        return lines;
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void probePoint(MapleMap map, BotNavigationGraph graph, Point point) {
        Foothold foothold = map.getFootholds().findBelow(point);
        int regionId = graph.findRegionId(map, point);
        BotNavigationGraph.Region region = graph.getRegion(regionId);

        System.out.printf("%nPoint %d,%d%n", point.x, point.y);
        System.out.printf("  below foothold=%s  region=%d%n", foothold == null ? "none" : foothold.getId(), regionId);
        if (region == null) {
            return;
        }

        System.out.printf("  region span=[%d,%d] y=[%d,%d] segments=%d%n",
                region.minX, region.maxX, region.minY, region.maxY, region.segments.size());

        Map<BotNavigationGraph.EdgeType, Integer> counts = new EnumMap<>(BotNavigationGraph.EdgeType.class);
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
            counts.merge(edge.type, 1, Integer::sum);
        }
        System.out.printf("  outgoing %s%n", counts);

        List<BotNavigationGraph.Edge> nearbyJumpEdges = findNearbyEdges(graph, region.id, point, BotNavigationGraph.EdgeType.JUMP, 24, 16);
        if (nearbyJumpEdges.isEmpty()) {
            System.out.println("  nearby jump edges: none");
            return;
        }

        System.out.println("  nearby jump edges:");
        for (BotNavigationGraph.Edge edge : nearbyJumpEdges) {
            System.out.printf("    %d,%d -> %d,%d  %s  toRegion=%d%n",
                    edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y, edgeDetails(edge), edge.toRegionId);
        }
    }

    private static void probeJump(MapleMap map, BotNavigationGraph graph, Point point) {
        int walkStep = BotMovementManager.walkStep(map, graph.movementProfile);
        int regionId = graph.findRegionId(map, point);

        System.out.printf("%nJump probe %d,%d  region=%d%n", point.x, point.y, regionId);
        for (int stepX : new int[]{-walkStep, 0, walkStep}) {
            BotMovementManager.JumpLanding landing = BotMovementManager.simulateJumpLanding(map, point, stepX, graph.movementProfile);
            if (landing == null) {
                System.out.printf("  stepX=%d -> no landing%n", stepX);
                continue;
            }

            int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
            System.out.printf("  stepX=%d -> %d,%d fh=%d region=%d%n",
                    stepX, landing.point().x, landing.point().y, landing.foothold().getId(), landingRegionId);
        }

        List<BotNavigationGraph.Edge> nearbyJumpEdges = regionId < 0
                ? List.of()
                : findNearbyEdges(graph, regionId, point, BotNavigationGraph.EdgeType.JUMP, 24, 16);
        if (nearbyJumpEdges.isEmpty()) {
            System.out.println("  graph jump edges near start: none");
            return;
        }

        System.out.println("  graph jump edges near start:");
        for (BotNavigationGraph.Edge edge : nearbyJumpEdges) {
            System.out.printf("    %d,%d -> %d,%d  %s  toRegion=%d%n",
                    edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y, edgeDetails(edge), edge.toRegionId);
        }
    }

    private static void probeRegion(BotNavigationGraph graph, int regionId) {
        BotNavigationGraph.Region region = graph.getRegion(regionId);
        System.out.printf("%nRegion %d%n", regionId);
        if (region == null) {
            System.out.println("  missing");
            return;
        }

        Map<BotNavigationGraph.EdgeType, Integer> counts = new EnumMap<>(BotNavigationGraph.EdgeType.class);
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
            counts.merge(edge.type, 1, Integer::sum);
        }

        System.out.printf("  span=[%d,%d] y=[%d,%d] segments=%d center=%d,%d%n",
                region.minX, region.maxX, region.minY, region.maxY, region.segments.size(),
                region.centerPoint().x, region.centerPoint().y);
        System.out.printf("  outgoing %s%n", counts);
    }

    private static void probeEdges(BotNavigationGraph graph, int regionId) {
        BotNavigationGraph.Region region = graph.getRegion(regionId);
        System.out.printf("%nEdges from region %d%n", regionId);
        if (region == null) {
            System.out.println("  missing");
            return;
        }

        List<BotNavigationGraph.Edge> edges = new ArrayList<>(graph.getOutgoing(regionId));
        edges.sort(Comparator.comparing((BotNavigationGraph.Edge edge) -> edge.type)
                .thenComparingInt(edge -> edge.toRegionId)
                .thenComparingInt(edge -> edge.startPoint.x)
                .thenComparingInt(edge -> edge.startPoint.y)
                .thenComparingInt(edge -> edge.endPoint.x)
                .thenComparingInt(edge -> edge.endPoint.y));
        if (edges.isEmpty()) {
            System.out.println("  none");
            return;
        }

        for (BotNavigationGraph.Edge edge : edges) {
            System.out.printf("  %s %d,%d -> %d,%d  %s  toRegion=%d  cost=%d%n",
                    edge.type, edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y,
                    edgeDetails(edge), edge.toRegionId, edge.cost);
        }
    }

    private static void probeRopes(BotNavigationGraph graph, MapleMap map) {
        System.out.printf("%nRopes (%d)%n", map.getRopes().size());
        int index = 0;
        for (server.maps.Rope rope : map.getRopes()) {
            index++;
            int climbEdges = countClimbEdgesForRope(graph, rope);
            System.out.printf("  %d. x=%d top=%d bottom=%d climbEdges=%d%n",
                    index, rope.x(), rope.topY(), rope.bottomY(), climbEdges);
        }
    }

    private static void probePath(MapleMap map, BotNavigationGraph graph, PathProbe pathProbe) {
        int startRegionId = graph.findRegionId(map, pathProbe.start);
        int targetRegionId = graph.findRegionId(map, pathProbe.target);
        List<BotNavigationGraph.Edge> path = startRegionId < 0 || targetRegionId < 0 || startRegionId == targetRegionId
                ? List.of()
                : BotNavigationManager.findPath(graph, map, pathProbe.start, startRegionId, targetRegionId, pathProbe.target);

        System.out.printf("%nPath probe %d,%d -> %d,%d  regions %d -> %d%n",
                pathProbe.start.x, pathProbe.start.y, pathProbe.target.x, pathProbe.target.y, startRegionId, targetRegionId);
        if (path.isEmpty()) {
            System.out.println("  no path");
            return;
        }

        for (int i = 0; i < path.size(); i++) {
            BotNavigationGraph.Edge edge = path.get(i);
            System.out.printf("  %d. %s %d,%d -> %d,%d  %s  toRegion=%d  cost=%d%n",
                    i + 1, edge.type, edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y,
                    edgeDetails(edge), edge.toRegionId, edge.cost);
        }
    }

    private static void probeRegionPath(BotNavigationGraph graph, RegionPathProbe regionPath) {
        BotNavigationGraph.Region targetRegion = graph.getRegion(regionPath.targetRegionId);
        Point targetPoint = targetRegion == null ? null : targetRegion.centerPoint();
        BotNavigationGraph.Region startRegion = graph.getRegion(regionPath.startRegionId);
        Point startPoint = startRegion == null ? null : startRegion.centerPoint();
        List<BotNavigationGraph.Edge> path = targetPoint == null
                ? List.of()
                : BotNavigationManager.findPath(graph, BotNavigationMapLoader.loadMapGeometry(graph.mapId),
                startPoint, regionPath.startRegionId, regionPath.targetRegionId, targetPoint);

        System.out.printf("%nRegion path %d -> %d%n", regionPath.startRegionId, regionPath.targetRegionId);
        if (startPoint == null || targetPoint == null || path.isEmpty()) {
            System.out.println("  no path");
            return;
        }

        System.out.printf("  target center %d,%d%n", targetPoint.x, targetPoint.y);
        for (int i = 0; i < path.size(); i++) {
            BotNavigationGraph.Edge edge = path.get(i);
            System.out.printf("  %d. %s %d,%d -> %d,%d  %s  toRegion=%d  cost=%d%n",
                    i + 1, edge.type, edge.startPoint.x, edge.startPoint.y, edge.endPoint.x, edge.endPoint.y,
                    edgeDetails(edge), edge.toRegionId, edge.cost);
        }
    }

    private static String edgeDetails(BotNavigationGraph.Edge edge) {
        List<String> parts = new ArrayList<>();
        if (edge.launchStepX != 0) {
            parts.add("stepX=" + edge.launchStepX);
        }
        if (edge.type == BotNavigationGraph.EdgeType.JUMP && edge.launchMinX != edge.launchMaxX) {
            parts.add("launchX=" + edge.launchMinX + ".." + edge.launchMaxX);
        }
        if (edge.portalId != 0) {
            parts.add("portal=" + edge.portalId);
        }
        if (edge.ropeX != 0 || edge.ropeTopY != 0 || edge.ropeBottomY != 0) {
            parts.add("rope=" + edge.ropeX + "," + edge.ropeTopY + "->" + edge.ropeBottomY);
        }
        return parts.isEmpty() ? "-" : String.join(" ", parts);
    }

    private static int countClimbEdgesForRope(BotNavigationGraph graph, server.maps.Rope rope) {
        int count = 0;
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != BotNavigationGraph.EdgeType.CLIMB) {
                    continue;
                }
                if (edge.ropeX == rope.x() && edge.ropeTopY == rope.topY() && edge.ropeBottomY == rope.bottomY()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static List<BotNavigationGraph.Edge> findNearbyEdges(BotNavigationGraph graph,
                                                                 int regionId,
                                                                 Point point,
                                                                 BotNavigationGraph.EdgeType type,
                                                                 int maxDx,
                                                                 int maxDy) {
        List<BotNavigationGraph.Edge> nearby = new ArrayList<>();
        for (BotNavigationGraph.Edge edge : graph.getOutgoing(regionId)) {
            if (edge.type != type) {
                continue;
            }
            if (Math.abs(edge.startPoint.x - point.x) > maxDx || Math.abs(edge.startPoint.y - point.y) > maxDy) {
                continue;
            }
            nearby.add(edge);
        }
        nearby.sort((left, right) -> {
            int leftDistance = Math.abs(left.startPoint.x - point.x) + Math.abs(left.startPoint.y - point.y);
            int rightDistance = Math.abs(right.startPoint.x - point.x) + Math.abs(right.startPoint.y - point.y);
            return Integer.compare(leftDistance, rightDistance);
        });
        return nearby;
    }

    private record PathProbe(Point start, Point target) {
    }

    private record RegionPathProbe(int startRegionId, int targetRegionId) {
    }
}
