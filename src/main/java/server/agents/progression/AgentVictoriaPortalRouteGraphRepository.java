package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Loads the generated standard-portal subset used by Victoria progression. */
public final class AgentVictoriaPortalRouteGraphRepository {
    private static final String DEFAULT_RESOURCE =
            "/agents/catalogs/victoria-portal-route-graph.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentVictoriaPortalRouteGraphRepository DEFAULT = load(DEFAULT_RESOURCE);

    private final AgentVictoriaPortalRouteGraph graph;

    AgentVictoriaPortalRouteGraphRepository(AgentVictoriaPortalRouteGraph graph) {
        this.graph = graph;
        Set<Long> unique = new HashSet<>();
        for (AgentVictoriaPortalRouteGraph.Edge edge : graph.edges()) {
            long key = ((long) edge.fromMapId() << 32) ^ (edge.toMapId() & 0xffffffffL);
            if (!unique.add(key)) {
                throw new IllegalArgumentException("duplicate Victoria portal edge "
                        + edge.fromMapId() + " -> " + edge.toMapId());
            }
        }
    }

    public static AgentVictoriaPortalRouteGraphRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentVictoriaPortalRouteGraph graph() {
        return graph;
    }

    private static AgentVictoriaPortalRouteGraphRepository load(String resourcePath) {
        try (InputStream input = AgentVictoriaPortalRouteGraphRepository.class
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria portal route graph: " + resourcePath);
            }
            return new AgentVictoriaPortalRouteGraphRepository(
                    MAPPER.readValue(input, AgentVictoriaPortalRouteGraph.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria portal route graph: "
                    + resourcePath, failure);
        }
    }
}
