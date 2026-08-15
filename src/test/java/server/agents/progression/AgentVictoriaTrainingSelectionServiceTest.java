package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentVictoriaTrainingSelectionServiceTest {
    @Test
    void levelFourteenCatchUpCanSelectFromLevelFifteenTrainingCatalog() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getMapId()).thenReturn(120010000);
        when(agent.getClient()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentVictoriaTrainingState state =
                entry.capabilityStates().require(AgentVictoriaTrainingState.STATE_KEY);
        state.start(15, false, 1_000L);

        AgentVictoriaTrainingCatalog.TrainingMap selected =
                AgentVictoriaTrainingSelectionService.select(
                        entry,
                        agent,
                        state,
                        AgentVictoriaTrainingCatalogRepository.defaultRepository(),
                        15,
                        1_000L)
                        .orElseThrow();
        Set<Integer> targets = AgentVictoriaTrainingSelectionService.targetMobIds(selected);

        assertNotEquals(120010000, selected.mapId());
        assertFalse(targets.isEmpty());
        assertTrue(selected.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role()))
                .mapToInt(AgentVictoriaTrainingCatalog.SpawnGroup::expectedCount)
                .sum() > 1);
        assertTrue(AgentVictoriaTrainingRouteCatalog.canRoute(120010000, selected.mapId()));
    }
}
