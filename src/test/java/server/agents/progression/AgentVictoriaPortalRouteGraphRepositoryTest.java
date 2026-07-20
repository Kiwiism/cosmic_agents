package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaPortalRouteGraphRepositoryTest {
    @Test
    void loadsGeneratedStandardVictoriaGraph() {
        AgentVictoriaPortalRouteGraph graph =
                AgentVictoriaPortalRouteGraphRepository.defaultRepository().graph();

        assertTrue(graph.edges().size() > 600);
        assertFalse(graph.sourceSha256().isBlank());
    }

    @Test
    void routesBetweenTownsAndOrdinaryTrainingMaps() {
        assertNotNull(AgentVictoriaTrainingRouteCatalog.nextHop(104000000, 101010101));
        assertNotNull(AgentVictoriaTrainingRouteCatalog.nextHop(102000000, 105050200));
        assertNotNull(AgentVictoriaTrainingRouteCatalog.nextHop(103000000, 101030103));
    }

    @Test
    void doesNotInventScriptedEntryPolicy() {
        assertNull(AgentVictoriaTrainingRouteCatalog.nextHop(100000000, 100020100));
        assertNull(AgentVictoriaTrainingRouteCatalog.nextHop(103000000, 103000101));
    }
}
