package server.agents.economy.activity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestObjectiveMapIndexTest {
    @Test
    void returnsGeneratedCombinedCandidateOrderForActiveQuest() {
        QuestObjectiveMapIndex index = new QuestObjectiveMapIndex(
                "/agents/catalogs/adaptive/victoria-quest-hunt-index.json");

        assertEquals(103020200, index.preferredMaps(Set.of(1115)).getFirst());
        assertTrue(index.preferredMaps(Set.of()).isEmpty());
    }

    @Test
    void activityCatalogContainsAuthoritativeMapleIslandAndVictoriaMaps() {
        VictoriaActivityMapCatalog catalog = new VictoriaActivityMapCatalog(
                "/agents/catalogs/adaptive/victoria-map-facts.json");

        assertTrue(catalog.candidates(7).stream().anyMatch(map -> map.mapId() < 10_000_000));
        assertTrue(catalog.candidates(7).stream().anyMatch(map -> map.mapId() >= 100_000_000));
    }
}
