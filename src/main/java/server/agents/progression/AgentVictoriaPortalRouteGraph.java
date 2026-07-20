package server.agents.progression;

import java.util.List;

/** Versioned standard-portal graph for ordinary Victoria Island travel. */
public record AgentVictoriaPortalRouteGraph(
        int schemaVersion,
        String catalogId,
        String sourcePath,
        String sourceSha256,
        List<Edge> edges) {

    public AgentVictoriaPortalRouteGraph {
        if (schemaVersion <= 0 || blank(catalogId) || blank(sourcePath) || blank(sourceSha256)
                || edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("a complete Victoria portal route graph is required");
        }
        edges = List.copyOf(edges);
    }

    public record Edge(int fromMapId, int toMapId) {
        public Edge {
            if (fromMapId <= 0 || toMapId <= 0 || fromMapId == toMapId) {
                throw new IllegalArgumentException("a portal edge requires two distinct maps");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
