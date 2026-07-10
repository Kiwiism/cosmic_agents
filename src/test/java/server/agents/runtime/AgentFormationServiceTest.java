package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentFormationService.FormationState;
import server.agents.capabilities.movement.AgentFormationService.FormationType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFormationServiceTest {
    @Test
    void preservesLegacyOffsetPatterns() {
        assertEquals(60, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(0, 4));
        assertEquals(-60, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(1, 4));
        assertEquals(120, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(2, 4));
        assertEquals(-120, new FormationState(FormationType.STAGGER, 60, 120).offsetFor(3, 4));
        assertEquals(0, new FormationState(FormationType.STACK, 0, 120).offsetFor(3, 4));
        assertEquals(0, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(0, 4));
        assertEquals(70, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(1, 4));
        assertEquals(-70, new FormationState(FormationType.SPREAD, 70, 120).offsetFor(2, 4));
        assertEquals(-120, new FormationState(FormationType.LEFT, 120, 120).offsetFor(0, 4));
        assertEquals(240, new FormationState(FormationType.RIGHT, 120, 120).offsetFor(1, 4));
    }

    @Test
    void appliesOffsetsToEntries() {
        AgentRuntimeEntry first = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        AgentFormationService.applyOffsets(
                List.of(first, second), new FormationState(FormationType.STAGGER, 60, 120));

        assertEquals(60, AgentFormationStateRuntime.followOffsetX(first));
        assertEquals(-60, AgentFormationStateRuntime.followOffsetX(second));
    }

    @Test
    void resolvesFormationStateByLeaderOrDefault() {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(100);
        FormationState defaultFormation = AgentFormationService.defaultStagger(60, 120);
        FormationState customFormation = new FormationState(FormationType.LEFT, 80, 90);
        Map<Integer, FormationState> formations = Map.of(leader.getId(), customFormation);

        assertEquals(customFormation, AgentFormationService.stateForLeader(formations, leader.getId(), defaultFormation));
        assertEquals(defaultFormation, AgentFormationService.stateForLeader(formations, 999, defaultFormation));
        assertEquals(customFormation, AgentFormationService.stateForEntry(
                new AgentRuntimeEntry(mock(Character.class), leader, null), formations, defaultFormation));
        assertEquals(defaultFormation, AgentFormationService.stateForEntry(
                new AgentRuntimeEntry(mock(Character.class), null, null), formations, defaultFormation));
    }

    @Test
    void exposesAgentOwnedFormationStore() {
        FormationState formation = new FormationState(FormationType.RIGHT, 70, 80);
        AgentFormationService.formationsByLeaderId().clear();

        AgentFormationService.formationsByLeaderId().put(123, formation);

        assertSame(formation, AgentFormationService.formationsByLeaderId().get(123));
        AgentFormationService.formationsByLeaderId().clear();
    }
}
