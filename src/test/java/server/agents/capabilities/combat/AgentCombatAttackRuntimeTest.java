package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCombatAttackRuntimeTest {
    @Test
    void blocksAttacksUntilMapTransitionAndWarmupAreComplete() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);

        when(map.hasTransitioningPlayerObserver()).thenReturn(true);
        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(false);
        assertFalse(AgentAttackExecutionProvider.mapReadyForAttack(agent));

        when(map.hasTransitioningPlayerObserver()).thenReturn(false);
        assertFalse(AgentAttackExecutionProvider.mapReadyForAttack(agent));

        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(true);
        assertTrue(AgentAttackExecutionProvider.mapReadyForAttack(agent));
    }

    @Test
    void deadPrimaryTargetIsRejectedAtFinalSendGate() {
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(false);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentAttackPlan plan = new AgentAttackPlan(
                0, 0, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 4, 0, 600, null);

        AgentCombatAttackRuntime.attackMonster(entry, agent, plan);

        assertFalse(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));
        verify(target).isAlive();
        verify(agent, never()).getMap();
    }
}
