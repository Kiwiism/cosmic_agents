package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaQuestRuntimeCatalogRepositoryTest {
    @Test
    void loadsConservativeLocalHuntingEntriesAndCuratedInteractionBridges() {
        AgentVictoriaQuestRuntimeCatalog catalog =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().catalog();

        assertEquals(72, catalog.entries().size());
        assertFalse(catalog.sourceRevision().isBlank());
        assertEquals(1, catalog.entries().stream().filter(entry -> entry.huntingObjectives().isEmpty()).count());
        assertTrue(catalog.entries().stream().anyMatch(entry ->
                entry.questId() == 2090 && entry.huntingObjectives().isEmpty()));
        assertTrue(catalog.entries().stream().flatMap(entry -> entry.huntingObjectives().stream())
                .allMatch(objective -> !objective.huntMaps().isEmpty()));
    }
}
