package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomHuntMapSelectorTest {
    @Test
    void fillsRankedMapsToTheirRecommendedMaximumBeforeOverflowing() {
        var maps = List.of(map(1, 4), map(2, 4), map(3, 4));

        assertDecision(maps, Map.of(1, 3, 2, 0, 3, 0), 1, false);
        assertDecision(maps, Map.of(1, 4, 2, 3, 3, 0), 2, false);
        assertDecision(maps, Map.of(1, 4, 2, 4, 3, 3), 3, false);
    }

    @Test
    void evenlyOverflowsEqualCapacityMapsUsingAuthoredRankAsTheTieBreaker() {
        var maps = List.of(map(1, 4), map(2, 4), map(3, 4));
        Map<Integer, Integer> occupancy = new HashMap<>(Map.of(1, 4, 2, 4, 3, 4));
        int[] expected = {1, 2, 3, 1};

        for (int mapId : expected) {
            var selected = AgentMushroomKingdomHuntMapSelector.select(maps, occupancy).orElseThrow();
            assertEquals(mapId, selected.map().mapId());
            assertTrue(selected.overflow());
            occupancy.merge(mapId, 1, Integer::sum);
        }

        assertEquals(Map.of(1, 6, 2, 5, 3, 5), occupancy);
    }

    @Test
    void overflowIsBalancedRelativeToDifferentRecommendedMaximums() {
        var maps = List.of(map(1, 3), map(2, 2));
        Map<Integer, Integer> occupancy = new HashMap<>(Map.of(1, 3, 2, 2));

        var first = AgentMushroomKingdomHuntMapSelector.select(maps, occupancy).orElseThrow();
        assertEquals(1, first.map().mapId());
        occupancy.merge(1, 1, Integer::sum);

        var second = AgentMushroomKingdomHuntMapSelector.select(maps, occupancy).orElseThrow();
        assertEquals(2, second.map().mapId());
        occupancy.merge(2, 1, Integer::sum);

        assertEquals(Map.of(1, 4, 2, 3), occupancy);
    }

    private static void assertDecision(List<AgentMushroomKingdomCatalog.HuntMap> maps,
                                       Map<Integer, Integer> occupancy,
                                       int expectedMapId,
                                       boolean overflow) {
        var selected = AgentMushroomKingdomHuntMapSelector.select(maps, occupancy).orElseThrow();
        assertEquals(expectedMapId, selected.map().mapId());
        if (overflow) assertTrue(selected.overflow());
        else assertFalse(selected.overflow());
    }

    private static AgentMushroomKingdomCatalog.HuntMap map(int mapId, int maximum) {
        return new AgentMushroomKingdomCatalog.HuntMap(mapId, "map-" + mapId, maximum);
    }
}
