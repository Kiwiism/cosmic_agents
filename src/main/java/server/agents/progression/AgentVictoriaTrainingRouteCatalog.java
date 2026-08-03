package server.agents.progression;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** WZ-verified standard Victoria graph plus scripted edges used by the level-15 slice. */
final class AgentVictoriaTrainingRouteCatalog {
    private static final Map<Integer, Set<Integer>> EDGES = edges();

    private AgentVictoriaTrainingRouteCatalog() {
    }

    static Integer nextHop(int source, int destination) {
        return nextHop(source, destination, Set.of());
    }

    static Integer nextHop(int source, int destination, Set<Long> excludedEdges) {
        if (source == destination) {
            return destination;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> previous = new HashMap<>();
        queue.add(source);
        previous.put(source, source);
        while (!queue.isEmpty() && !previous.containsKey(destination)) {
            int current = queue.removeFirst();
            for (int next : EDGES.getOrDefault(current, Set.of())) {
                if (excludedEdges != null && excludedEdges.contains(edgeKey(current, next))) {
                    continue;
                }
                if (previous.putIfAbsent(next, current) == null) {
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(destination)) {
            return null;
        }
        int step = destination;
        while (previous.get(step) != source) {
            step = previous.get(step);
        }
        return step;
    }

    static boolean canRoute(int source, int destination) {
        return source == destination || nextHop(source, destination) != null;
    }

    static int distance(int source, int destination) {
        if (source == destination) {
            return 0;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<Integer, Integer> distances = new HashMap<>();
        queue.add(source);
        distances.put(source, 0);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int nextDistance = distances.get(current) + 1;
            for (int next : EDGES.getOrDefault(current, Set.of())) {
                if (distances.putIfAbsent(next, nextDistance) != null) {
                    continue;
                }
                if (next == destination) {
                    return nextDistance;
                }
                queue.addLast(next);
            }
        }
        return -1;
    }

    static long edgeKey(int source, int destination) {
        return ((long) source << 32) ^ (destination & 0xffffffffL);
    }

    static Integer scriptedPortalId(int source, int destination) {
        for (AgentVictoriaLevel15Catalog.ScriptedPortal portal : catalog().scriptedPortals()) {
            if (portal.sourceMapId() == source && portal.destinationMapId() == destination) {
                return portal.portalId();
            }
        }
        return null;
    }

    private static Map<Integer, Set<Integer>> edges() {
        Map<Integer, java.util.LinkedHashSet<Integer>> mutable = new LinkedHashMap<>();
        for (AgentVictoriaLevel15Catalog.RouteCorridor route : catalog().routeCorridors()) {
            List<Integer> corridor = route.mapIds();
            for (int i = 1; i < corridor.size(); i++) {
                int left = corridor.get(i - 1);
                int right = corridor.get(i);
                mutable.computeIfAbsent(left, ignored -> new java.util.LinkedHashSet<>()).add(right);
                mutable.computeIfAbsent(right, ignored -> new java.util.LinkedHashSet<>()).add(left);
            }
        }
        for (AgentVictoriaPortalRouteGraph.Edge edge :
                AgentVictoriaPortalRouteGraphRepository.defaultRepository().graph().edges()) {
            mutable.computeIfAbsent(edge.fromMapId(), ignored -> new java.util.LinkedHashSet<>())
                    .add(edge.toMapId());
        }
        for (AgentVictoriaLevel15Catalog.Career career : catalog().careers()) {
            AgentVictoriaLevel15Catalog.TrainingGround trainingGround = career.trainingGround();
            if (trainingGround == null) {
                continue;
            }
            for (int instanceMapId : trainingGround.instanceMapIds()) {
                mutable.computeIfAbsent(instanceMapId, ignored -> new java.util.LinkedHashSet<>())
                        .add(trainingGround.entranceMapId());
            }
        }
        Map<Integer, Set<Integer>> result = new LinkedHashMap<>();
        mutable.forEach((mapId, adjacent) -> result.put(mapId,
                Collections.unmodifiableSet(new java.util.LinkedHashSet<>(adjacent))));
        return Map.copyOf(result);
    }

    private static AgentVictoriaLevel15Catalog catalog() {
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog();
    }
}
