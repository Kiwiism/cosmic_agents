package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentCombatVariationRuntimeTest {
    @Test
    void disabledVariationAlwaysKeepsBestTarget() {
        AgentRuntimeEntry entry = entry();

        assertEquals(0, AgentCombatVariationRuntime.selectTargetIndex(entry, 10));
        assertFalse(AgentCombatVariationRuntime.isPlatformAnchorRole(entry));
    }

    @Test
    void forcedMiddleSelectionStaysInsideMiddleThirdOfBoundedShortlist() {
        AgentRuntimeEntry entry = entry();
        AgentCombatVariationRuntime.configure(entry,
                new AgentCombatVariationSettings(91L, true, 1.0d, 10, false, 0.0d));

        for (int i = 0; i < 20; i++) {
            int selected = AgentCombatVariationRuntime.selectTargetIndex(entry, 30);
            assertTrue(selected >= 3 && selected <= 6);
        }
    }

    @Test
    void targetChoicesAreReplayableForTheSameAgentSeed() {
        AgentRuntimeEntry first = entry();
        AgentRuntimeEntry replay = entry();
        AgentCombatVariationSettings settings =
                new AgentCombatVariationSettings(92L, true, 0.55d, 10, false, 0.0d);
        AgentCombatVariationRuntime.configure(first, settings);
        AgentCombatVariationRuntime.configure(replay, settings);

        List<Integer> firstChoices = new ArrayList<>();
        List<Integer> replayChoices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            firstChoices.add(AgentCombatVariationRuntime.selectTargetIndex(first, 10));
            replayChoices.add(AgentCombatVariationRuntime.selectTargetIndex(replay, 10));
        }

        assertEquals(firstChoices, replayChoices);
        assertTrue(firstChoices.stream().anyMatch(index -> index == 0));
        assertTrue(firstChoices.stream().anyMatch(index -> index > 0));
    }

    @Test
    void forcedAnchorRoleUsesMiddleTargetsAndCanBeCleared() {
        AgentRuntimeEntry entry = entry();
        AgentCombatVariationRuntime.configure(entry,
                new AgentCombatVariationSettings(93L, true, 0.0d, 10, true, 1.0d));

        assertTrue(AgentCombatVariationRuntime.isPlatformAnchorRole(entry));
        assertTrue(AgentCombatVariationRuntime.selectTargetIndex(entry, 10) > 0);

        AgentCombatVariationRuntime.clear(entry);
        assertFalse(AgentCombatVariationRuntime.isPlatformAnchorRole(entry));
        assertEquals(0, AgentCombatVariationRuntime.selectTargetIndex(entry, 10));
    }

    @Test
    void automaticPlatformAnchorSurvivesSameObjectiveAndClearsWhenMobChanges() {
        AgentRuntimeEntry entry = entry();
        AgentCombatVariationState state = entry.capabilityStates()
                .require(AgentCombatVariationState.STATE_KEY);
        AgentPatrolStateRuntime.startPatrol(entry, 7, 10000);
        state.markAutomaticAnchor(100100, 10000, 7);

        assertTrue(AgentCombatVariationRuntime.isAutomaticPlatformAnchor(entry));
        AgentCombatVariationRuntime.retainAutomaticAnchorFor(entry, List.of(100100, 100101));
        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));

        AgentCombatVariationRuntime.retainAutomaticAnchorFor(entry, List.of(100200));
        assertFalse(AgentCombatVariationRuntime.isAutomaticPlatformAnchor(entry));
        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
    }

    @Test
    void automaticAnchorCleanupDoesNotClearAReplacementManualPatrol() {
        AgentRuntimeEntry entry = entry();
        AgentCombatVariationState state = entry.capabilityStates()
                .require(AgentCombatVariationState.STATE_KEY);
        state.markAutomaticAnchor(100100, 10000, 7);
        AgentPatrolStateRuntime.startPatrol(entry, 8, 10000);

        AgentCombatVariationRuntime.clearAutomaticAnchor(entry);

        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(8, AgentPatrolStateRuntime.patrolRegionId(entry));
        assertFalse(AgentCombatVariationRuntime.isAutomaticPlatformAnchor(entry));
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }
}
