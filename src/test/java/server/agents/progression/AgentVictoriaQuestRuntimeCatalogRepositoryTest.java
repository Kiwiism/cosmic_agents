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

        assertEquals(79, catalog.entries().size());
        assertFalse(catalog.sourceRevision().isBlank());
        assertEquals(1, catalog.entries().stream().filter(entry ->
                entry.huntingObjectives().isEmpty()
                        && entry.shopProcurementObjectives().isEmpty()).count());
        assertTrue(catalog.entries().stream().anyMatch(entry ->
                entry.questId() == 2090 && entry.huntingObjectives().isEmpty()
                        && entry.shopProcurementObjectives().isEmpty()));
        assertTrue(catalog.entries().stream().anyMatch(entry ->
                entry.questId() == 2165 && entry.huntingObjectives().isEmpty()
                        && entry.shopProcurementObjectives().stream().anyMatch(objective ->
                        objective.targetId() == 2000006 && objective.requiredCount() == 1)));
        assertTrue(catalog.entries().stream().anyMatch(entry ->
                entry.questId() == 2209 && entry.huntingObjectives().isEmpty()
                        && entry.shopProcurementObjectives().stream().anyMatch(objective ->
                        objective.targetId() == 2010004 && objective.requiredCount() == 1)));
        assertTrue(catalog.entries().stream().flatMap(entry ->
                        entry.shopProcurementObjectives().stream())
                .flatMap(objective -> objective.shopSources().stream())
                .allMatch(source -> source.npcId() > 0 && source.mapId() > 0
                        && source.unitPrice() > 0));
        assertTrue(catalog.entries().stream().flatMap(entry -> entry.huntingObjectives().stream())
                .allMatch(objective -> !objective.huntMaps().isEmpty()));
        AgentVictoriaQuestRuntimeCatalog.StartItemRequirement necklace = catalog.entries().stream()
                .filter(entry -> entry.questId() == 28257)
                .findFirst().orElseThrow().startItemRequirements().getFirst();
        assertEquals(4032496, necklace.itemId());
        assertEquals("Devil Hunter's Necklace", necklace.itemName());
        assertEquals(java.util.List.of(28179, 28198, 28219, 28238, 28256),
                necklace.producerQuestIds());
        assertTrue(catalog.entries().stream()
                .map(AgentVictoriaQuestRuntimeCatalog.Entry::questId)
                .filter(questId -> questId >= 28257 && questId <= 28261)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(java.util.Set.of(28257, 28258, 28259, 28260, 28261)));
        assertEquals(java.util.Set.of(28257, 28258, 28259, 28260, 28261),
                catalog.entries().stream()
                        .filter(entry -> entry.questId() == 28262)
                        .findFirst().orElseThrow().prerequisiteRequirements().stream()
                        .map(AgentVictoriaQuestRuntimeCatalog.PrerequisiteRequirement::questId)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
