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
        assertEquals(768, maps.stream()
                .mapToInt(AgentFieldObservationCatalog.MapPreset::maximumAgents).sum());
        assertEquals(93, maps.stream().map(AgentFieldObservationCatalog.MapPreset::mapId).distinct().count());
        assertEquals(21, repository.maps("recommended").size());
        assertEquals(72, repository.maps("exploratory").size());
        assertTrue(maps.stream().allMatch(map -> map.level() >= 15 && map.level() <= 25));
        assertTrue(maps.stream().anyMatch(map -> map.maximumAgents() > 12));
        assertTrue(maps.stream().allMatch(map -> map.recommendedMinimum() >= 1
                && map.recommendedMinimum() <= map.recommendedMaximum()
                && map.recommendedMaximum() <= map.maximumAgents()));
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
    void henesysHuntingGroundCapacityReflectsFourPopulatedPlatforms() {
        var map = AgentFieldObservationCatalogRepository.defaultRepository()
                .find(104040000).orElseThrow();

        assertEquals(4, map.recommendedMinimum());
        assertEquals(7, map.recommendedMaximum());
        assertEquals(9, map.maximumAgents());
        assertEquals(java.util.List.of(4, 7, 9, 7, 4), map.activeCounts());
        assertEquals(java.util.List.of(5, 4), map.partySizes());
        assertEquals("generated", map.capacitySource());
    }

    @Test
    void bubblingSubwayCanExceedLegacyTwelveAgentCeiling() {
        var map = AgentFieldObservationCatalogRepository.defaultRepository()
                .find(103000101).orElseThrow();

        assertEquals(16, map.maximumAgents());
        assertEquals(java.util.List.of(6, 5, 5), map.partySizes());
    }

    @Test
    void generatedCapacitySnapshotCarriesExplainablePolicyAndEvidence() {
        var catalog = AgentFieldCapacityCatalogRepository.load();

        assertEquals(93, catalog.maps().size());
        assertEquals(500, catalog.policy().agentSpacingPx());
        assertEquals(4, catalog.policy().spawnEntriesPerAgent());
        assertEquals(15, catalog.policy().fragmentationPenaltyPercent());
        assertEquals(40, catalog.policy().minimumActivePercent());
        assertTrue(catalog.maps().stream().allMatch(map ->
                map.maximumAgents() <= map.totalSpawnEntries()
                        && !map.platforms().isEmpty()));
    }

    @Test
    void numberedMapNavigationUsesStableCatalogOrderAndWraps() {
        var repository = AgentFieldObservationCatalogRepository.defaultRepository();

        assertEquals(93, repository.numberedMaps().size());
        assertEquals(100020000, repository.numberedMap(1).orElseThrow().map().mapId());
        assertEquals(100000004, repository.numberedMap(23).orElseThrow().map().mapId());
        assertEquals(120010000, repository.numberedMap(93).orElseThrow().map().mapId());
        assertTrue(repository.numberedMap(0).isEmpty());
        assertTrue(repository.numberedMap(94).isEmpty());

        assertEquals(1, repository.relativeMap(120010000, 1).number());
        assertEquals(93, repository.relativeMap(100020000, -1).number());
        assertEquals(1, repository.relativeMap(100000000, 1).number());
        assertEquals(93, repository.relativeMap(100000000, -1).number());
        assertEquals(79, repository.numberedMapForMapId(104040001).orElseThrow().number());
    }

    @Test
    void largeMapsUseStableSixOrSmallerPartyPartitions() {
        for (var map : AgentFieldObservationCatalogRepository.defaultRepository().maps()) {
            assertEquals(map.maximumAgents(), map.partySizes().stream().mapToInt(Integer::intValue).sum());
            assertTrue(map.partySizes().stream().allMatch(size -> size >= 1 && size <= 6));
            assertTrue(map.activeCounts().stream().allMatch(
                    count -> count >= (map.maximumAgents() * 40 + 99) / 100
                            && count <= map.maximumAgents()));
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
        assertEquals(421, ids.size());
        assertEquals(389, AgentFieldObservationEquipmentRepository.npcShopItemIds().size());
        assertEquals(196, AgentFieldObservationEquipmentRepository.victoriaDropItemIds().size());
        assertTrue(AgentFieldObservationEquipmentRepository.victoriaDropItemIds().contains(1_072_291));
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(itemId -> itemId >= 1_000_000 && itemId < 2_000_000));
        assertTrue(java.util.Collections.disjoint(ids, Set.of(
                1002140, 1002518, 1002959, 1032032, 1042003, 1050127, 1051140,
                1062007, 1082145, 1082146, 1082147, 1082148, 1082149, 1082150, 1322013)));
        assertEquals(AgentFieldObservationEquipmentCatalog.ALLOWED_SOURCE_SLOTS,
                AgentFieldObservationEquipmentRepository.sourceSlots());
    }
}
