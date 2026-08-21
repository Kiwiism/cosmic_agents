package server.agents.progression.questcatalog;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestCatalogRepositoryTest {
    private final AgentQuestCatalogRepository repository =
            AgentQuestCatalogRepository.defaultRepository();

    @Test
    void exposesEveryGeneratedVictoriaQuestAsAnIndividualDefinition() {
        AgentQuestCatalog catalog = repository.catalog();

        assertEquals(183, catalog.entries().size());
        assertEquals(183, catalog.entries().stream().map(AgentQuestDefinition::questId).distinct().count());
        assertEquals(83, catalog.entries().stream()
                .filter(entry -> entry.objectives().stream()
                        .anyMatch(objective -> !objective.huntMaps().isEmpty()))
                .count());
        assertFalse(catalog.generatedRevision().isBlank());
        assertTrue(catalog.entries().stream()
                .filter(entry -> !entry.start().complete() || !entry.completion().complete())
                .allMatch(entry -> !entry.autonomousStartAllowed()
                        && entry.selectionDisposition() == AgentQuestSelectionDisposition.REVIEW_BLOCKED));
    }

    @Test
    void joinsGeneratedHuntMapsWithoutTurningThemIntoLocks() {
        AgentQuestDefinition luke = repository.find(2018).orElseThrow();

        assertTrue(luke.objectives().stream().anyMatch(objective ->
                objective.targetId() == 4000034 && !objective.huntMaps().isEmpty()));
        assertEquals(20, luke.recommendedLevel());
        assertEquals(6000, luke.attemptRequirements().minimumHitChanceBasisPoints());
        assertTrue(luke.recommendationRationale().contains("60%"));
    }

    @Test
    void evaluatesAccuracyInventorySuppliesAndPrerequisitesAsLiveFacts() {
        AgentQuestEligibility weak = repository.evaluate(2018,
                new AgentQuestEligibilityContext(20, 100, 5900, 10, 100, 100, Map.of()));
        AgentQuestEligibility ready = repository.evaluate(2018,
                new AgentQuestEligibilityContext(20, 100, 6000, 3, 30, 10, Map.of()));
        AgentQuestEligibility chained = repository.evaluate(2003,
                new AgentQuestEligibilityContext(20, 100, 10_000, 10, 100, 100, Map.of()));

        assertEquals(AgentQuestEligibility.Status.ACCURACY_INSUFFICIENT, weak.status());
        assertTrue(ready.eligible());
        assertEquals(AgentQuestEligibility.Status.PREREQUISITE_LOCKED, chained.status());
        assertTrue(repository.evaluate(2003,
                new AgentQuestEligibilityContext(20, 100, 10_000, 10, 100, 100,
                        Map.of(2002, 2))).eligible());
    }
}
