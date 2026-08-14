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
        }
        Map<Integer, Map<Integer, Integer>> spawnsByRegion = new LinkedHashMap<>();
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
            int widthCapacity = Math.max(1, (region.width() + 599) / 600);
            int spawnCapacity = Math.max(1, (population + 7) / 8);
            int capacity = Math.min(3, Math.min(widthCapacity, spawnCapacity));
            result.add(new AgentFarmingCell(
                    cellIdByRegion.get(regionId),
                    agent.getMapId(),
                    Set.of(regionId),
                    liveCounts,
                    expectedCounts,
                    anchors(region),
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

    private static List<AgentFarmingAnchor> anchors(AgentNavigationGraph.Region region) {
        int inset = Math.min(48, Math.max(0, region.width() / 4));
        int leftX = region.minX + inset;
        int rightX = region.maxX - inset;
        int centerX = region.minX + region.width() / 2;
        LinkedHashMap<String, Point> candidates = new LinkedHashMap<>();
        candidates.put("center", region.pointAt(centerX));
        candidates.put("left", region.pointAt(leftX));
        candidates.put("right", region.pointAt(rightX));
        int score = 100;
        List<AgentFarmingAnchor> anchors = new ArrayList<>();
        for (Map.Entry<String, Point> candidate : candidates.entrySet()) {
            if (anchors.stream().anyMatch(anchor -> anchor.position().equals(candidate.getValue()))) {
                continue;
            }
            anchors.add(new AgentFarmingAnchor(
                    "region-" + region.id + '-' + candidate.getKey(), candidate.getValue(), score));
            score = Math.max(0, score - 10);
        }
        return List.copyOf(anchors);
    }
}
