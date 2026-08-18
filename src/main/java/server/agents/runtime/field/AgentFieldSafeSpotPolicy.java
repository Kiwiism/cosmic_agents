package server.agents.runtime.field;

import client.Character;
import server.agents.field.AgentFarmingCell;
import server.agents.field.AgentFieldSafeSpotCatalogRepository;
import server.agents.field.AgentNavigationFarmingCellCatalog;
import server.agents.field.AgentFieldPolicyConfig;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.SpawnPoint;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Portal-near spawn-free rest selection with authored overrides and a low-density fallback. */
public final class AgentFieldSafeSpotPolicy {
    private AgentFieldSafeSpotPolicy() {
    }

    public static Point select(
            AgentRuntimeEntry entry, Character agent, Set<Integer> relevantMobIds) {
        Point authored = authored(agent, 0);
        if (authored != null) {
            return authored;
        }
        Point generated = generated(entry, agent, 0);
        if (generated != null) {
            return generated;
        }
        List<AgentFarmingCell> cells = AgentNavigationFarmingCellCatalog.INSTANCE.cells(entry, agent);
        Point origin = agent == null ? null : agent.getPosition();
        return cells.stream().filter(cell -> !cell.transitOnly())
                .min(Comparator.comparingLong(cell -> score(cell, relevantMobIds, origin)))
                .map(cell -> cell.centralAnchor().position()).orElse(null);
    }

    public static Point staging(AgentRuntimeEntry entry, Character agent, int ordinal) {
        Point authored = authored(agent, ordinal);
        return authored != null ? authored : generated(entry, agent, ordinal);
    }

    /** Resolves an inactive staging point before the Agent exists on the destination map. */
    public static Point staging(MapleMap map, AgentNavigationGraph graph, int ordinal) {
        if (map == null || graph == null) {
            return null;
        }
        Point authored = authored(map.getId(), ordinal);
        return authored != null ? authored : generated(map, graph, ordinal);
    }

    /** Chooses the closest legitimate player-spawn portal that can reach the staging point. */
    public static Point nearestEntry(
            MapleMap map, AgentNavigationGraph graph, Point staging) {
        if (map == null || graph == null || staging == null) {
            return null;
        }
        int stagingRegion = AgentNavigationRegionService.resolvePointTargetRegionId(graph, map, staging);
        int stagingComponent = graph.connectedComponentId(stagingRegion);
        return map.getPortals().stream()
                .filter(AgentFieldSafeSpotPolicy::isPlayerSpawn)
                .map(portal -> portalCandidate(graph, map, portal))
                .filter(java.util.Objects::nonNull)
                .filter(portal -> stagingComponent < 0 || portal.componentId() == stagingComponent)
                .min(Comparator.comparingLong(portal -> squaredDistance(portal.position(), staging)))
                .map(portal -> new Point(portal.position()))
                .orElse(null);
    }

    private static Point authored(Character agent, int ordinal) {
        return agent == null ? null : authored(agent.getMapId(), ordinal);
    }

    private static Point authored(int mapId, int ordinal) {
        return AgentFieldSafeSpotCatalogRepository.defaultRepository()
                .spot(mapId, ordinal).orElse(null);
    }

    /**
     * Generates a deterministic mob-spawn-free point for any field map. Candidates share a
     * connected component with a portal, remain at least 180 px from every authored monster spawn,
     * and are ordered by distance to the closest portal. This keeps the policy applicable to the
     * whole observation catalog without maintaining 93 copies of navigation geometry.
     */
    private static Point generated(AgentRuntimeEntry entry, Character agent, int ordinal) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return null;
        }
        MapleMap map = agent.getMap();
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map, AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent));
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(
                    entry, map, AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent));
            return null;
        }
        return generated(map, graph, ordinal);
    }

    private static Point generated(MapleMap map, AgentNavigationGraph graph, int ordinal) {
        List<Point> spawns = map.getMonsterSpawn().stream()
                .filter(java.util.Objects::nonNull)
                .map(SpawnPoint::getPosition)
                .filter(java.util.Objects::nonNull)
                .map(Point::new)
                .toList();
        Set<Integer> spawnRegions = spawns.stream()
                .map(point -> AgentNavigationRegionService.resolvePointTargetRegionId(
                        graph, map, point))
                .filter(regionId -> regionId >= 0)
                .collect(java.util.stream.Collectors.toSet());
        List<PortalCandidate> portals = map.getPortals().stream()
                .filter(AgentFieldSafeSpotPolicy::isPlayerSpawn)
                .map(portal -> portalCandidate(graph, map, portal))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (portals.isEmpty()) {
            return null;
        }
        Set<Integer> portalComponents = portals.stream()
                .map(PortalCandidate::componentId)
                .filter(component -> component >= 0)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        int sampleStepPx = AgentFieldPolicyConfig.safeSpotSampleStepPx();
        int spawnClearancePx = AgentFieldPolicyConfig.safeSpotSpawnClearancePx();
        long clearanceSq = (long) spawnClearancePx * spawnClearancePx;
        ArrayList<PointScore> candidates = new ArrayList<>();
        for (AgentNavigationGraph.Region region : graph.regions) {
            if (region == null || region.isRopeRegion || region.width() < sampleStepPx
                    || !portalComponents.contains(graph.connectedComponentId(region.id))) {
                continue;
            }
            for (int x = region.minX; x <= region.maxX; x += sampleStepPx) {
                Point point = region.pointAt(x);
                long nearestSpawnSq = spawns.stream().mapToLong(spawn -> squaredDistance(point, spawn))
                        .min().orElse(Long.MAX_VALUE);
                if (nearestSpawnSq < clearanceSq) {
                    continue;
                }
                long nearestPortalSq = portals.stream()
                        .mapToLong(portal -> squaredDistance(point, portal.position()))
                        .min().orElse(Long.MAX_VALUE);
                candidates.add(new PointScore(
                        point, spawnRegions.contains(region.id), nearestPortalSq, nearestSpawnSq));
            }
        }
        candidates.sort(Comparator.comparing(PointScore::spawnRegion)
                .thenComparingLong(PointScore::portalDistanceSq)
                .thenComparing(Comparator.comparingLong(PointScore::spawnClearanceSq).reversed())
                .thenComparingInt(candidate -> candidate.position().x));
        if (candidates.isEmpty()) {
            return new Point(portals.get(Math.floorMod(ordinal, portals.size())).position());
        }
        ArrayList<Point> spread = new ArrayList<>();
        long separationSq = (long) sampleStepPx * sampleStepPx;
        for (PointScore candidate : candidates) {
            if (spread.stream().allMatch(point -> squaredDistance(point, candidate.position()) >= separationSq)) {
                spread.add(candidate.position());
            }
        }
        return new Point(spread.get(Math.floorMod(ordinal, spread.size())));
    }

    private static PortalCandidate portalCandidate(
            AgentNavigationGraph graph, MapleMap map, Portal portal) {
        Point raw = new Point(portal.getPosition());
        int regionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                graph, map, raw);
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region == null || region.isRopeRegion) {
            return null;
        }
        int componentId = graph.connectedComponentId(regionId);
        return componentId < 0 ? null : new PortalCandidate(region.pointAt(raw.x), componentId);
    }

    private static boolean isPlayerSpawn(Portal portal) {
        return portal != null && portal.getPosition() != null
                && portal.getType() >= 0 && portal.getType() <= Portal.TELEPORT_PORTAL
                && portal.getTargetMapId() == constants.id.MapId.NONE;
    }

    private static long squaredDistance(Point left, Point right) {
        long dx = (long) left.x - right.x;
        long dy = (long) left.y - right.y;
        return dx * dx + dy * dy;
    }

    private record PortalCandidate(Point position, int componentId) {
    }

    private record PointScore(
            Point position, boolean spawnRegion, long portalDistanceSq, long spawnClearanceSq) {
    }

    private static long score(AgentFarmingCell cell, Set<Integer> relevantMobIds, Point origin) {
        int population = cell.relevantPopulation(relevantMobIds);
        Point anchor = cell.centralAnchor().position();
        long distance = origin == null ? 0L : Math.round(Math.sqrt(origin.distanceSq(anchor)));
        return population * AgentFieldPolicyConfig.safeSpotPopulationWeight()
                + (cell.deadEnd() ? AgentFieldPolicyConfig.safeSpotDeadEndPenalty() : 0L)
                + distance;
    }
}
