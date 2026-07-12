package server.agents.capabilities.primitive;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPrimitiveLegacyParityTest {
    @Test
    void navigationAdapterProducesLegacyMoveStateWithoutConsumingMovementTick() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry legacy = entry(agent);
        AgentRuntimeEntry adapted = entry(agent);
        Point destination = new Point(120, 15);
        legacy.combatCooldownState().setAttackCooldownMs(75);
        adapted.combatCooldownState().setAttackCooldownMs(75);
        AgentModeService.startMoveTo(legacy, destination, true);

        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.mapId(agent)).thenReturn(10000);
        when(gateway.position(agent)).thenReturn(new Point(0, 0));
        doAnswer(invocation -> {
            AgentModeService.startMoveTo(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(gateway).navigate(any(), any(), any(Boolean.class));

        var step = new AgentNavigationCapability(gateway).tick(
                context(adapted, agent),
                new AgentNavigationCapability.Command(10000, destination, 5, true));

        assertFalse(step.consumedTick());
        assertEquals(AgentMoveTargetStateRuntime.moveTarget(legacy), AgentMoveTargetStateRuntime.moveTarget(adapted));
        assertEquals(AgentMoveTargetStateRuntime.isPrecise(legacy), AgentMoveTargetStateRuntime.isPrecise(adapted));
        assertEquals(legacy.combatCooldownState().attackCooldownMs(),
                adapted.combatCooldownState().attackCooldownMs());
    }

    @Test
    void combatAdapterProducesLegacyGrindStateAndAddsOnlyObjectiveConstraint() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry legacy = entry(agent);
        AgentRuntimeEntry adapted = entry(agent);
        legacy.combatCooldownState().setAttackCooldownMs(75);
        adapted.combatCooldownState().setAttackCooldownMs(75);
        AgentModeService.startGrind(legacy, AgentMovementStateResetService::clearNavigationState);

        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.alive(agent)).thenReturn(true);
        when(gateway.questProgress(agent, 1037, 100100)).thenReturn(0);
        when(gateway.liveMonsterCount(agent, Set.of(100100))).thenReturn(1);
        doAnswer(invocation -> {
            AgentRuntimeEntry entry = invocation.getArgument(0);
            Set<Integer> allowed = invocation.getArgument(1);
            AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, allowed);
            AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
            return null;
        }).when(gateway).grind(any(), any());

        var step = new AgentCombatCapability(gateway).tick(
                context(adapted, agent),
                new AgentCombatCapability.Command(1037, Map.of(100100, 10)));

        assertFalse(step.consumedTick());
        assertEquals(AgentModeStateRuntime.grinding(legacy), AgentModeStateRuntime.grinding(adapted));
        assertNull(legacy.grindTargetState().target());
        assertNull(adapted.grindTargetState().target());
        assertEquals(legacy.combatCooldownState().attackCooldownMs(),
                adapted.combatCooldownState().attackCooldownMs());
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(adapted, 100100));
        assertFalse(AgentCombatObjectiveTargetStateRuntime.allows(adapted, 9300012));
    }

    private static AgentRuntimeEntry entry(Character agent) {
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }

    private static AgentCapabilityContext context(AgentRuntimeEntry entry, Character agent) {
        return new AgentCapabilityContext(entry, agent, 100L, 0L, 0, null);
    }
}
