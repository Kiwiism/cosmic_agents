package server.agents.capabilities.combat;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        AgentAttackTransactionResult result = AgentCombatAttackRuntime.attackMonster(entry, agent, plan);

        assertEquals(AgentAttackTransactionResult.Status.REJECTED, result.status());
        assertEquals(AgentAttackTransactionResult.Reason.TARGET_UNAVAILABLE, result.reason());
        assertFalse(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));
        verify(target).isAlive();
        verify(agent, never()).getMap();
    }

    @Test
    void targetFromAnotherMapIsRejectedByAuthoritativeGate() {
        Character agent = mock(Character.class);
        MapleMap agentMap = mock(MapleMap.class);
        MapleMap targetMap = mock(MapleMap.class);
        Monster target = mock(Monster.class);
        Inventory equipped = mock(Inventory.class);
        when(agent.getMap()).thenReturn(agentMap);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agentMap.hasTransitioningPlayerObserver()).thenReturn(false);
        when(agentMap.isMobPhysicsObserverWarmupComplete()).thenReturn(true);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(targetMap);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentAttackPlan plan = new AgentAttackPlan(
                0, 0, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 4, 0, 600, null);

        AgentAttackTransactionResult result = AgentCombatAttackRuntime.attackMonster(entry, agent, plan);

        assertEquals(AgentAttackTransactionResult.Status.REJECTED, result.status());
        assertEquals(AgentAttackTransactionResult.Reason.TARGET_NOT_IN_AGENT_MAP, result.reason());
        assertFalse(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));
    }
}
