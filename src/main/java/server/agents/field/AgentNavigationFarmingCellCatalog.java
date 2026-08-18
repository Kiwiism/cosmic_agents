package server.agents.field;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.model.AgentPosition;
import server.agents.perception.AgentMobPerception;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.SpawnPoint;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates farming cells without changing or duplicating the navigation graph. */
public final class AgentNavigationFarmingCellCatalog implements AgentFarmingCellCatalog {
    public static final AgentNavigationFarmingCellCatalog INSTANCE =
            new AgentNavigationFarmingCellCatalog();

    private AgentNavigationFarmingCellCatalog() {
    }

    @Override
    public List<AgentFarmingCell> cells(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return List.of();
        }
        var profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(agent.getMap(), profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(entry, agent.getMap(), profile);
            return List.of();
        }
        AgentPerceptionSnapshot perception =
                CosmicAgentPerceptionSnapshotFactory.capture(agent, System.currentTimeMillis());
        return build(agent, graph, perception.mobs(), agent.getMap().getMonsterSpawn());
    }

    static List<AgentFarmingCell> build(
            Character agent,
            AgentNavigationGraph graph,
            List<AgentMobPerception> mobs,
            List<SpawnPoint> spawnPoints) {
        if (agent == null || agent.getMap() == null || graph == null
                || mobs == null || spawnPoints == null) {
            return List.of();
        }
        Map<Integer, Map<Integer, Integer>> mobsByRegion = new LinkedHashMap<>();
        Map<Integer, List<Point>> livePositionsByRegion = new LinkedHashMap<>();
        for (AgentMobPerception mob : mobs) {
            if (!mob.alive() || mob.position() == null) {
                continue;
            }
            AgentPosition position = mob.position();
            int regionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                    graph, agent.getMap(), new Point(position.x(), position.y()));
            AgentNavigationGraph.Region region = graph.getRegion(regionId);
            if (regionId < 0 || region == null || region.isRopeRegion) {
                continue;
            }
            mobsByRegion.computeIfAbsent(regionId, ignored -> new HashMap<>())
                    .merge(mob.mobId(), 1, Integer::sum);
            livePositionsByRegion.computeIfAbsent(regionId, ignored -> new ArrayList<>())
                    .add(new Point(position.x(), position.y()));
        }
        Map<Integer, Map<Integer, Integer>> spawnsByRegion = new LinkedHashMap<>();
        Map<Integer, List<Point>> spawnPositionsByRegion = new LinkedHashMap<>();
        for (SpawnPoint spawn : spawnPoints) {
            if (spawn == null || spawn.getPosition() == null) {
                continue;
            }
            int regionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                    graph, agent.getMap(), spawn.getPosition());
            AgentNavigationGraph.Region region = graph.getRegion(regionId);
            if (regionId < 0 || region == null || region.isRopeRegion) {
                continue;
            }
            spawnsByRegion.computeIfAbsent(regionId, ignored -> new HashMap<>())
                    .merge(spawn.getMonsterId(), 1, Integer::sum);
            spawnPositionsByRegion.computeIfAbsent(regionId, ignored -> new ArrayList<>())
                    .add(new Point(spawn.getPosition()));
        }
        Set<Integer> farmingRegionIds = new LinkedHashSet<>(spawnsByRegion.keySet());
        farmingRegionIds.addAll(mobsByRegion.keySet());
        if (farmingRegionIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> cellIdByRegion = new HashMap<>();
        farmingRegionIds.forEach(regionId ->
                cellIdByRegion.put(regionId, "region-" + regionId));
        List<AgentFarmingCell> result = new ArrayList<>();
        for (Integer regionId : farmingRegionIds) {
            AgentNavigationGraph.Region region = graph.getRegion(regionId);
            Set<String> adjacentCells = nearestFarmingNeighbors(
                    graph, regionId, farmingRegionIds).stream()
                    .map(cellIdByRegion::get).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<Integer, Integer> liveCounts = mobsByRegion.getOrDefault(regionId, Map.of());
            Map<Integer, Integer> expectedCounts = spawnsByRegion.getOrDefault(regionId, Map.of());
            int population = (expectedCounts.isEmpty() ? liveCounts : expectedCounts)
                    .values().stream().mapToInt(Integer::intValue).sum();
            int capacity = AgentFieldCapacityEstimator.platformCapacity(region.width(), population);
            List<Point> populationPositions = spawnPositionsByRegion.getOrDefault(
                    regionId, livePositionsByRegion.getOrDefault(regionId, List.of()));
            result.add(new AgentFarmingCell(
                    cellIdByRegion.get(regionId),
                    agent.getMapId(),
                    Set.of(regionId),
                    liveCounts,
                    expectedCounts,
                    stations(region, populationPositions, capacity),
                    adjacentCells,
                    capacity,
                    adjacentCells.size() <= 1,
                    false));
        }
        result.sort(Comparator.comparing(AgentFarmingCell::cellId));
        return List.copyOf(result);
    }

    private static Set<Integer> nearestFarmingNeighbors(
            AgentNavigationGraph graph, int sourceRegionId, Set<Integer> farmingRegionIds) {
        LinkedHashSet<Integer> visited = new LinkedHashSet<>();
        LinkedHashSet<Integer> frontier = new LinkedHashSet<>();
        frontier.add(sourceRegionId);
        visited.add(sourceRegionId);
        while (!frontier.isEmpty()) {
            LinkedHashSet<Integer> next = new LinkedHashSet<>();
            LinkedHashSet<Integer> found = new LinkedHashSet<>();
            for (Integer regionId : frontier) {
                for (Integer neighbor : graph.getMutualAdjacentRegionIds(regionId)) {
                    if (!visited.add(neighbor)) {
                        continue;
                    }
                    if (farmingRegionIds.contains(neighbor)) {
                        found.add(neighbor);
                    } else {
                        next.add(neighbor);
                    }
                }
            }
            if (!found.isEmpty()) {
                return Set.copyOf(found);
            }
            frontier = next;
        }
        return Set.of();
    }

    /** Spawn-weighted station slots with midpoint (one-dimensional Voronoi) territories. */
    private static List<AgentFarmingAnchor> stations(
            AgentNavigationGraph.Region region, List<Point> populationPositions, int capacity) {
        List<Integer> populationXs = populationPositions.stream()
                .map(point -> Math.max(region.minX, Math.min(region.maxX, point.x)))
                .sorted().toList();
        int stationCount = Math.max(1, capacity);
        ArrayList<Integer> stationXs = new ArrayList<>(stationCount);
        for (int index = 0; index < stationCount; index++) {
            int x;
            if (populationXs.isEmpty()) {
                x = region.minX + (int) Math.round(
                        (index + 0.5d) * Math.max(1, region.width()) / stationCount);
            } else {
                int quantileIndex = Math.min(populationXs.size() - 1,
                        (int) Math.floor((index + 0.5d) * populationXs.size() / stationCount));
                x = populationXs.get(quantileIndex);
            }
            int minimumX = index == 0 ? region.minX : stationXs.get(index - 1) + 1;
            int maximumX = region.maxX - Math.max(0, stationCount - index - 1);
            stationXs.add(Math.max(minimumX, Math.min(maximumX, x)));
        }
        ArrayList<AgentFarmingAnchor> stations = new ArrayList<>(stationCount);
        for (int index = 0; index < stationCount; index++) {
            int x = stationXs.get(index);
            int territoryMinX = index == 0
                    ? region.minX : midpoint(stationXs.get(index - 1), x) + 1;
            int territoryMaxX = index == stationCount - 1
                    ? region.maxX : midpoint(x, stationXs.get(index + 1));
            stations.add(new AgentFarmingAnchor(
                    "region-" + region.id + "-station-" + index,
                    region.pointAt(x), 100, territoryMinX, territoryMaxX));
        }
        return List.copyOf(stations);
    }

    private static int midpoint(int left, int right) {
        return left + (right - left) / 2;
    }
}
