package server.agents.capabilities.navigation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies sparse, versioned content corrections to otherwise generic graph search.
 */
final class AgentNavigationRouteOverlayPolicy {
    private static final String RESOURCE = "/agents/catalogs/navigation-route-overlays.json";
    private static final AgentNavigationRouteOverlayPolicy DEFAULT = load();

    private record RouteKey(int mapId, int graphVersion, int targetRegionId) {
    }

    record Catalog(int schemaVersion, List<Route> routes) {
    }

    record Route(int mapId,
                 int graphVersion,
                 int targetRegionId,
                 List<Integer> regionPath,
                 String rationale) {
    }

    private final Map<RouteKey, Map<Integer, Integer>> nextRegionByRoute;
    private final Map<RouteKey, String> rationaleByRoute;

    private AgentNavigationRouteOverlayPolicy(Catalog catalog) {
        Map<RouteKey, Map<Integer, Integer>> routes = new HashMap<>();
        Map<RouteKey, String> rationales = new HashMap<>();
        for (Route route : catalog.routes()) {
            if (route.regionPath() == null || route.regionPath().size() < 2) {
                throw new IllegalArgumentException("navigation route overlay requires at least two regions");
            }
            Map<Integer, Integer> nextByRegion = new HashMap<>();
            for (int i = 0; i < route.regionPath().size() - 1; i++) {
                Integer previous = nextByRegion.put(route.regionPath().get(i), route.regionPath().get(i + 1));
                if (previous != null) {
                    throw new IllegalArgumentException("navigation route overlay repeats region "
                            + route.regionPath().get(i));
                }
            }
            RouteKey key = new RouteKey(route.mapId(), route.graphVersion(), route.targetRegionId());
            if (routes.put(key, Map.copyOf(nextByRegion)) != null) {
                throw new IllegalArgumentException("duplicate navigation route overlay " + key);
            }
            rationales.put(key, route.rationale() == null ? "" : route.rationale());
        }
        nextRegionByRoute = Map.copyOf(routes);
        rationaleByRoute = Map.copyOf(rationales);
    }

    static boolean allows(AgentNavigationGraph graph,
                          int targetRegionId,
                          AgentNavigationGraph.Edge edge) {
        if (graph == null || edge == null) {
            return true;
        }
        Map<Integer, Integer> nextByRegion = DEFAULT.nextRegionByRoute.get(
                new RouteKey(graph.mapId, graph.version, targetRegionId));
        if (nextByRegion == null) {
            return true;
        }
        Integer requiredNextRegion = nextByRegion.get(edge.fromRegionId);
        return requiredNextRegion == null || requiredNextRegion == edge.toRegionId;
    }

    static boolean applies(AgentNavigationGraph graph, int targetRegionId) {
        return graph != null && DEFAULT.nextRegionByRoute.containsKey(
                new RouteKey(graph.mapId, graph.version, targetRegionId));
    }

    static String rationale(AgentNavigationGraph graph, int targetRegionId) {
        if (graph == null) {
            return "";
        }
        return DEFAULT.rationaleByRoute.getOrDefault(
                new RouteKey(graph.mapId, graph.version, targetRegionId), "");
    }

    private static AgentNavigationRouteOverlayPolicy load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentNavigationRouteOverlayPolicy.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing navigation route overlays: " + RESOURCE);
            }
            Catalog catalog = mapper.readValue(input, Catalog.class);
            if (catalog.schemaVersion() != 1) {
                throw new IllegalStateException("unsupported navigation route overlay schema "
                        + catalog.schemaVersion());
            }
            return new AgentNavigationRouteOverlayPolicy(catalog);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load navigation route overlays: " + RESOURCE, failure);
        }
    }
}
