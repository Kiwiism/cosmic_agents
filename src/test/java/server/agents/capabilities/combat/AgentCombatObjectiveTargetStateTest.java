package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AgentGrindTargetStateRuntime.setTarget(entry, retainedTarget);
        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, Set.of(100100));
        assertTrue(AgentGrindTargetStateRuntime.target(entry) == retainedTarget);

        AgentCombatObjectiveTargetStateRuntime.clear(entry);
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 9300012));
    }
}
