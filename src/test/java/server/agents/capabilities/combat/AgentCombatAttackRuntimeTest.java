package server.agents.capabilities.combat;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import constants.skills.Pirate;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.CombatAttackApplicationResult;
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
    void sharedHandlerResultIsTheAuthoritativeApplicationSignal() {
        assertTrue(CombatAttackApplicationResult.appliedResult().applied());
        assertFalse(CombatAttackApplicationResult.rejected(
                CombatAttackApplicationResult.Reason.HANDLER_REJECTED).applied());
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

    @Test
    void equipmentSwapRejectsAStaleWeaponIncompatibleSkillPlan() {
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentAttackPlan staleFlashFistPlan = new AgentAttackPlan(
                Pirate.FLASH_FIST, 1, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 4, 0, 600, null);

        try (MockedStatic<AgentAttackExecutionProvider> execution = Mockito.mockStatic(
                AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            execution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(agent))
                    .thenReturn(WeaponType.GUN);

            AgentAttackTransactionResult result = AgentCombatAttackRuntime.attackMonster(
                    entry, agent, staleFlashFistPlan);

            assertEquals(AgentAttackTransactionResult.Status.DEFERRED, result.status());
            assertEquals(AgentAttackTransactionResult.Reason.CANNOT_USE_SKILL, result.reason());
            verify(agent, never()).getMap();
        }
    }
}
