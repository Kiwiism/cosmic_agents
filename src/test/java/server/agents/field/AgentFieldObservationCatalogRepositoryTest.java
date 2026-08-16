package server.agents.field;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldObservationCatalogRepositoryTest {
    @Test
    void manifestSeparatesRecommendedAndExploratoryVictoriaMaps() {
        var repository = AgentFieldObservationCatalogRepository.defaultRepository();
        var maps = repository.maps();

        assertEquals(93, maps.size());
        assertEquals(403, maps.stream()
                .mapToInt(AgentFieldObservationCatalog.MapPreset::maximumAgents).sum());
        assertEquals(93, maps.stream().map(AgentFieldObservationCatalog.MapPreset::mapId).distinct().count());
        assertEquals(21, repository.maps("recommended").size());
        assertEquals(72, repository.maps("exploratory").size());
        assertTrue(maps.stream().allMatch(map -> map.level() >= 15 && map.level() <= 25));
        assertTrue(maps.stream().allMatch(map -> map.maximumAgents() <= 12));
    }

    @Test
    void henesysHuntingGroundUpperMapsAreAvailableForObservation() {
        var repository = AgentFieldObservationCatalogRepository.defaultRepository();
        var second = repository.find(104040001).orElseThrow();
        var third = repository.find(104040002).orElseThrow();

        assertEquals("exploratory", second.group());
        assertEquals(Set.of(1110100, 1210100, 1210101, 1210102), second.allowedMobIds());
        assertEquals("exploratory", third.group());
        assertEquals(Set.of(1110100, 1210101, 1210102), third.allowedMobIds());
    }

    @Test
    void largeMapsUseStableSixOrSmallerPartyPartitions() {
        for (var map : AgentFieldObservationCatalogRepository.defaultRepository().maps()) {
            assertEquals(map.maximumAgents(), map.partySizes().stream().mapToInt(Integer::intValue).sum());
            assertTrue(map.partySizes().stream().allMatch(size -> size >= 1 && size <= 6));
            assertTrue(map.activeCounts().stream().allMatch(
                    count -> count >= 1 && count <= map.maximumAgents()));
        }
    }

    @Test
    void highLevelHazardsAreNotEligibleCombatTargets() {
        var pigBeach = AgentFieldObservationCatalogRepository.defaultRepository().find(104010001).orElseThrow();
        var wildBoar = AgentFieldObservationCatalogRepository.defaultRepository().find(101040001).orElseThrow();

        assertEquals(Set.of(4090000), pigBeach.excludedMobIds());
        assertEquals(Set.of(3230300, 3230301), wildBoar.excludedMobIds());
        assertFalse(pigBeach.allowedMobIds().contains(4090000));
        assertTrue(java.util.Collections.disjoint(wildBoar.allowedMobIds(), wildBoar.excludedMobIds()));
    }

    @Test
    void equipmentSourcePoolIsExhaustiveAndDeduplicated() {
        var ids = AgentFieldObservationEquipmentRepository.itemIds();
        assertEquals(408, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(itemId -> itemId >= 1_000_000 && itemId < 2_000_000));
    }
}
