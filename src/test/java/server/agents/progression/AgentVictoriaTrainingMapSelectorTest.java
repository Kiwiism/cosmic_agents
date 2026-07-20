package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaTrainingMapSelectorTest {
    private final AgentVictoriaTrainingMapSelector selector = new AgentVictoriaTrainingMapSelector(
            AgentVictoriaTrainingCatalogRepository.defaultRepository());

    @Test
    void fillsRankedMapsToRecommendedCapacityBeforeFallingThrough() {
        AgentVictoriaTrainingMapSelector.Selection first = selector.select(
                20, 0, Map.of(100000003, 1), null).orElseThrow();
        assertEquals(100000003, first.map().mapId());

        AgentVictoriaTrainingMapSelector.Selection second = selector.select(
                20, 0, Map.of(100000003, 2), null).orElseThrow();
        assertEquals(100030001, second.map().mapId());
        assertTrue(second.reason().contains("highest-ranked"));
    }

    @Test
    void keepsAnEligibleCurrentMapAcrossLevelsButNotADeepFallback() {
        AgentVictoriaTrainingMapSelector.Selection retained = selector.select(
                21, 106000000, Map.of(106000000, 2), null).orElseThrow();
        assertEquals(106000000, retained.map().mapId());
        assertTrue(retained.reason().contains("retain"));

        AgentVictoriaTrainingMapSelector.Selection replaced = selector.select(
                24, 104010001, Map.of(), null).orElseThrow();
        assertEquals(101030102, replaced.map().mapId());
    }

    @Test
    void respectsHardCapacityAndExternalRouteEligibility() {
        Set<Integer> eligible = Set.of(105050100, 101030001);
        AgentVictoriaTrainingMapSelector.Selection selected = selector.select(
                25, 0, Map.of(105050100, 4), eligible).orElseThrow();
        assertEquals(101030001, selected.map().mapId());
    }
}
