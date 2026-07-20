package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaQuestRuntimeCatalogRepositoryTest {
    @Test
    void loadsOnlyConservativeLocalHuntingEntries() {
        AgentVictoriaQuestRuntimeCatalog catalog =
                AgentVictoriaQuestRuntimeCatalogRepository.defaultRepository().catalog();

        assertEquals(71, catalog.entries().size());
        assertFalse(catalog.sourceRevision().isBlank());
        assertTrue(catalog.entries().stream().allMatch(entry -> !entry.huntingObjectives().isEmpty()));
        assertTrue(catalog.entries().stream().flatMap(entry -> entry.huntingObjectives().stream())
                .allMatch(objective -> !objective.huntMaps().isEmpty()));
    }
}
