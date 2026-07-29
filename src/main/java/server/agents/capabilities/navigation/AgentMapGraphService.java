package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.integration.AgentPresence;
import server.life.NPC;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class AgentMapGraphService {
    private AgentMapGraphService() {
    }

    public record MovementProfileView(int speed, int jump) {
        static MovementProfileView from(AgentMovementProfile profile) {
            return new MovementProfileView(profile.totalSpeedStat(), profile.totalJumpStat());
        }
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
    }

    public record RegionView(int id,
                             String kind,
                             boolean ladder,
                             int minX,
                             int minY,
                             int maxX,
                             int maxY,
                             int centerX,
                             int centerY,
                             List<List<Integer>> segments,
                             List<String> report) {
    }

    public record EdgeView(String type,
                           int fromRegion,
                           int toRegion,
                           int cost,
                           int launchStepX,
                           int parallelCount,
                           int fromX,
                           int fromY,
                           int toX,
                           int toY) {
    }

    public record NpcView(int x, int y, String name) {
    }

    public record PortalView(int x, int y, String kind, int targetMapId, String name) {
    }

    public record CharacterView(int x, int y, String name, boolean agent) {
    }

    public record MapGraphView(int mapId,
                               String name,
                               int version,
                               MovementProfileView activeProfile,
                               List<MovementProfileView> profiles,
                               Bounds bounds,
                               List<RegionView> regions,
                               List<EdgeView> edges,
                               List<NpcView> npcs,
                               List<PortalView> portals,
                               List<CharacterView> characters) {
    }

    public record RouteView(int mapId,
                            int fromRegion,
                            int toRegion,
                            String mode,
                            boolean reached,
                            boolean bestEffort,
                            boolean capped,
                            int finalRegion,
                            Integer cost,
                            int expandedNodes,
                            double elapsedMs,
                            List<EdgeView> path) {
    }

    private record EdgeKey(int fromRegion, int toRegion, AgentNavigationGraph.EdgeType type) {
    }

    public static MapGraphView graphView(MapleMap map,
                                         AgentNavigationGraph graph,
                                         List<AgentMovementProfile> cachedProfiles) {
        if (map == null || graph == null) {
            throw new IllegalArgumentException("Map and navigation graph are required");
        }

        List<RegionView> regions = graph.regions.stream()
                .sorted(Comparator.comparingInt(region -> region.id))
                .map(region -> regionView(graph, region))
                .toList();
        List<EdgeView> edges = collapsedEdges(graph);
        List<NpcView> npcs = npcViews(map);
        List<PortalView> portals = portalViews(map);
        List<CharacterView> characters = characterViews(map);

        List<MovementProfileView> profiles = new ArrayList<>();
        profiles.add(MovementProfileView.from(graph.movementProfile));
        if (cachedProfiles != null) {
            for (AgentMovementProfile profile : cachedProfiles) {
                MovementProfileView view = MovementProfileView.from(profile);
                if (!profiles.contains(view)) {
                    profiles.add(view);
                }
            }
        }
        profiles.sort(Comparator
                .comparingInt(MovementProfileView::speed)
                .thenComparingInt(MovementProfileView::jump));

        String mapName = map.getMapName();
        if (mapName == null || mapName.isBlank()) {
            mapName = "Map " + map.getId();
        }
        return new MapGraphView(
                map.getId(),
                mapName,
                graph.version,
                MovementProfileView.from(graph.movementProfile),
                List.copyOf(profiles),
                bounds(regions),
                regions,
                edges,
                npcs,
                portals,
                characters);
    }

    public static RouteView testRoute(MapleMap map,
                                      AgentNavigationGraph graph,
                                      int fromRegionId,
                                      int toRegionId,
                                      boolean exhaustive) {
        AgentNavigationGraph.Region fromRegion = graph == null ? null : graph.getRegion(fromRegionId);
        AgentNavigationGraph.Region toRegion = graph == null ? null : graph.getRegion(toRegionId);
        if (map == null || graph == null || fromRegion == null || toRegion == null) {
            throw new IllegalArgumentException("Map, graph, source region and target region are required");
        }

        Point start = fromRegion.centerPoint();
        Point target = toRegion.centerPoint();
        long startedAt = System.nanoTime();
        AgentNavigationPathService.SearchOutcome outcome = AgentNavigationPathService.runSearch(
                graph,
                map,
                start,
                fromRegionId,
                toRegionId,
                target,
                exhaustive ? "mapgraph-exhaustive" : "committed",
                false,
                false,
                exhaustive ? Integer.MAX_VALUE : AgentNavigationPathService.MAX_EDGE_CHECKS);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0d;

        List<EdgeView> path = outcome.path().stream()
                .map(edge -> edgeView(edge, 1))
                .toList();
        Integer cost = outcome.cost() == Integer.MAX_VALUE ? null : outcome.cost();
        return new RouteView(
                map.getId(),
                fromRegionId,
                toRegionId,
                exhaustive ? "exhaustive" : "normal",
                outcome.reached(),
                outcome.bestEffort(),
                outcome.capped(),
                outcome.finalRegionId(),
                cost,
                outcome.expandedNodes(),
                elapsedMs,
                path);
    }

    static List<String> describeRegion(AgentNavigationGraph graph, int regionId) {
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region == null) {
            return List.of("Region " + regionId + " was not found.");
        }

        List<String> lines = new ArrayList<>();
        String kind = region.isRopeRegion ? (region.isLadder ? "ladder" : "rope") : "platform";
        lines.add("Region " + region.id + " — " + kind
                + "  x[" + region.minX + ".." + region.maxX + "]"
                + " y[" + region.minY + ".." + region.maxY + "]");
        if (region.isRopeRegion) {
            lines.add("Rope span " + region.height() + "px at x=" + region.minX);
        } else {
            String footholds = region.segments.stream()
                    .map(segment -> Integer.toString(segment.footholdId))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            boolean forbidFallDown = region.segments.stream().anyMatch(segment -> segment.forbidFallDown);
            lines.add("Footholds (" + region.segments.size() + "): " + footholds
                    + (forbidFallDown ? "  [forbidFallDown]" : ""));
        }

        Map<Integer, EnumSet<AgentNavigationGraph.EdgeType>> byDestination = new TreeMap<>();
        for (AgentNavigationGraph.Edge edge : graph.getOutgoing(regionId)) {
            if (edge.fromRegionId != edge.toRegionId) {
                byDestination.computeIfAbsent(
                                edge.toRegionId,
                                ignored -> EnumSet.noneOf(AgentNavigationGraph.EdgeType.class))
                        .add(edge.type);
            }
        }
        lines.add("Outgoing connections: " + byDestination.size());
        Set<Integer> mutual = graph.getMutualAdjacentRegionIds(regionId);
        for (Map.Entry<Integer, EnumSet<AgentNavigationGraph.EdgeType>> entry : byDestination.entrySet()) {
            String types = entry.getValue().stream()
                    .map(Enum::name)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            lines.add((mutual.contains(entry.getKey()) ? "↔ " : "→ ")
                    + "R" + entry.getKey() + ": " + types);
        }
        return List.copyOf(lines);
    }

    private static RegionView regionView(AgentNavigationGraph graph, AgentNavigationGraph.Region region) {
        List<List<Integer>> segments = region.segments.stream()
                .map(segment -> List.of(segment.x1, segment.y1, segment.x2, segment.y2))
                .toList();
        Point center = region.centerPoint();
        return new RegionView(
                region.id,
                region.isRopeRegion ? "rope" : "foothold",
                region.isLadder,
                region.minX,
                region.minY,
                region.maxX,
                region.maxY,
                center.x,
                center.y,
                segments,
                describeRegion(graph, region.id));
    }

    private static List<EdgeView> collapsedEdges(AgentNavigationGraph graph) {
        Map<EdgeKey, Integer> counts = new HashMap<>();
        Map<EdgeKey, AgentNavigationGraph.Edge> representative = new LinkedHashMap<>();
        for (AgentNavigationGraph.Region region : graph.regions) {
            for (AgentNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.fromRegionId == edge.toRegionId) {
                    continue;
                }
                EdgeKey key = new EdgeKey(edge.fromRegionId, edge.toRegionId, edge.type);
                counts.merge(key, 1, Integer::sum);
                AgentNavigationGraph.Edge current = representative.get(key);
                if (current == null || edge.cost < current.cost) {
                    representative.put(key, edge);
                }
            }
        }
        return representative.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<EdgeKey, AgentNavigationGraph.Edge> entry)
                                -> entry.getKey().fromRegion())
                        .thenComparingInt(entry -> entry.getKey().toRegion())
                        .thenComparing(entry -> entry.getKey().type()))
                .map(entry -> edgeView(entry.getValue(), counts.get(entry.getKey())))
                .toList();
    }

    private static EdgeView edgeView(AgentNavigationGraph.Edge edge, int parallelCount) {
        return new EdgeView(
                edge.type.name(),
                edge.fromRegionId,
                edge.toRegionId,
                edge.cost,
                edge.launchStepX,
                parallelCount,
                edge.startPoint.x,
                edge.startPoint.y,
                edge.endPoint.x,
                edge.endPoint.y);
    }

    private static Bounds bounds(List<RegionView> regions) {
        if (regions.isEmpty()) {
            return new Bounds(-400, -300, 400, 300);
        }
        int minX = regions.stream().mapToInt(RegionView::minX).min().orElse(-400);
        int minY = regions.stream().mapToInt(RegionView::minY).min().orElse(-300);
        int maxX = regions.stream().mapToInt(RegionView::maxX).max().orElse(400);
        int maxY = regions.stream().mapToInt(RegionView::maxY).max().orElse(300);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static List<NpcView> npcViews(MapleMap map) {
        return map.getMapObjectsInRange(
                        new Point(),
                        Double.POSITIVE_INFINITY,
                        List.of(MapObjectType.NPC)).stream()
                .filter(NPC.class::isInstance)
                .map(NPC.class::cast)
                .filter(npc -> npc.getPosition() != null)
                .map(npc -> new NpcView(
                        npc.getPosition().x,
                        npc.getPosition().y,
                        npc.getName() == null ? "" : npc.getName()))
                .sorted(Comparator.comparing(NpcView::name).thenComparingInt(NpcView::x))
                .toList();
    }

    private static List<PortalView> portalViews(MapleMap map) {
        return map.getPortals().stream()
                .filter(portal -> portal.getPosition() != null)
                .map(portal -> new PortalView(
                        portal.getPosition().x,
                        portal.getPosition().y,
                        portalKind(map.getId(), portal),
                        portal.getTargetMapId(),
                        portal.getName() == null ? "" : portal.getName()))
                .sorted(Comparator.comparingInt(PortalView::x).thenComparingInt(PortalView::y))
                .toList();
    }

    private static String portalKind(int mapId, Portal portal) {
        int type = portal.getType();
        if (type == 3 || type == 9 || type == 12 || type == 13) {
            return "collision";
        }
        int targetMapId = portal.getTargetMapId();
        if (targetMapId == mapId) {
            return "in-map";
        }
        if (targetMapId > 0 && targetMapId != 999999999) {
            return "cross-map";
        }
        return "spawn";
    }

    private static List<CharacterView> characterViews(MapleMap map) {
        return map.getAllPlayers().stream()
                .filter(character -> character.getPosition() != null)
                .map(character -> new CharacterView(
                        character.getPosition().x,
                        character.getPosition().y,
                        character.getName(),
                        AgentPresence.isAgent(character)))
                .sorted(Comparator.comparing(CharacterView::name))
                .toList();
    }
}
