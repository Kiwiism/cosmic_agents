package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaTrainingCatalogRepositoryTest {
    @Test
    void loadsARankedPlanForEveryLevelFromFifteenThroughThirty() {
        AgentVictoriaTrainingCatalogRepository repository =
                AgentVictoriaTrainingCatalogRepository.defaultRepository();

        assertEquals("victoria-level15-30-training-v1", repository.catalog().catalogId());
        assertEquals(23, repository.catalog().trainingMaps().size());
        assertEquals(IntStream.rangeClosed(15, 30).boxed().toList(),
                repository.catalog().levelPlans().stream()
                        .map(AgentVictoriaTrainingCatalog.LevelPlan::level)
                        .sorted()
                        .toList());
        for (int level = 15; level <= 30; level++) {
            assertTrue(repository.choicesForLevel(level).size() >= 5,
                    "level " + level + " needs enough choices to avoid a single-map swarm");
            assertEquals(IntStream.rangeClosed(1, repository.choicesForLevel(level).size()).boxed().toList(),
                    repository.choicesForLevel(level).stream()
                            .map(AgentVictoriaTrainingCatalog.TrainingChoice::rank)
                            .sorted()
                            .toList());
        }
        assertEquals(List.of(), repository.choicesForLevel(14));
        assertEquals(List.of(), repository.choicesForLevel(31));
    }

    @Test
    void preservesTheClassicRoutesWithoutMakingThemTheOnlyChoices() {
        AgentVictoriaTrainingCatalogRepository repository =
                AgentVictoriaTrainingCatalogRepository.defaultRepository();

        assertChoice(repository, 15, 101010101); // The Tree That Grew II
        assertChoice(repository, 17, 104010001); // Pig Beach
        assertChoice(repository, 20, 105050000); // Ant Tunnel I
        assertChoice(repository, 25, 101030001); // The Land of Wild Boar II
        assertChoice(repository, 27, 101020009); // Tree Dungeon, Forest Up North
        assertChoice(repository, 30, 105050200); // Ant Tunnel III
    }

    @Test
    void keepsDropTablesBehindARegeneratableVersionedOverlay() {
        AgentVictoriaTrainingCatalog.DropOverlayContract overlay =
                AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().dropOverlay();

        assertEquals(1, overlay.schemaVersion());
        assertEquals("tools/game-catalog/Export-VictoriaTrainingDropOverlay.ps1", overlay.generatorPath());
        assertEquals("tmp/game-catalog/generated_drop_catalog.json", overlay.defaultDropCatalogPath());
    }

    @Test
    void declaresTopDownCapacityAndTwoLevelFallbackPolicy() {
        AgentVictoriaTrainingCatalog.SelectionPolicy policy =
                AgentVictoriaTrainingCatalogRepository.defaultRepository().catalog().selectionPolicy();

        assertEquals("ranked-soft-capacity-then-hard-capacity", policy.rankingMode());
        assertTrue(policy.preserveCurrentMapWhenEligible());
        assertEquals(4, policy.currentMapMaximumRank());
        assertEquals(2, policy.fallbackLevelLookback());
        assertEquals("recommendedAgents", policy.softCapacityField());
        assertEquals("maximumAgents", policy.hardCapacityField());
    }

    private static void assertChoice(AgentVictoriaTrainingCatalogRepository repository,
                                     int level,
                                     int mapId) {
        assertTrue(repository.choicesForLevel(level).stream().anyMatch(choice -> choice.mapId() == mapId),
                () -> "level " + level + " is missing map " + mapId);
    }
}
