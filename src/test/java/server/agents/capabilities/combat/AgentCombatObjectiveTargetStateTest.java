package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatObjectiveTargetStateTest {
    @Test
    void emptyFilterPreservesLegacyEligibilityAndScopedFilterRestrictsTargets() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(
                mock(Character.class), mock(Character.class), null);

        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 100100));
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 9300012));

        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, Set.of(100100));
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 100100));
        assertFalse(AgentCombatObjectiveTargetStateRuntime.allows(entry, 9300012));

        Monster allowed = mock(Monster.class);
        Monster unrelated = mock(Monster.class);
        when(allowed.getId()).thenReturn(100100);
        when(unrelated.getId()).thenReturn(9300012);
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allowedMonsters(
                entry, java.util.List.of(allowed, unrelated)).equals(java.util.List.of(allowed)));

        Monster retainedTarget = mock(Monster.class);
        when(retainedTarget.getId()).thenReturn(100100);
        AgentGrindTargetStateRuntime.setTarget(entry, retainedTarget);
        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, Set.of(100100));
        assertSame(retainedTarget, AgentGrindTargetStateRuntime.target(entry));

        AgentCombatObjectiveTargetStateRuntime.setTargetPreferences(
                entry, Set.of(9300012), Set.of(100100));
        assertSame(retainedTarget, AgentGrindTargetStateRuntime.target(entry));
        assertTrue(AgentCombatObjectiveTargetStateRuntime.prefers(entry, 9300012));
        assertFalse(AgentCombatObjectiveTargetStateRuntime.prefers(entry, 100100));

        AgentCombatObjectiveTargetStateRuntime.setTargetPreferences(
                entry, Set.of(9300012), Set.of());
        assertTrue(AgentGrindTargetStateRuntime.target(entry) == null);

        AgentCombatObjectiveTargetStateRuntime.clear(entry);
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 9300012));
    }
}
