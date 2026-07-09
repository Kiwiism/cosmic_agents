package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipmentOptimizerHooksTest {
    @Test
    void gatewayHooksMirrorLiveMetadataLookups() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip equip = mock(Equip.class);
        Map<String, Integer> stats = Map.of("reqSTR", 12);
        when(inventory.isTwoHandedWeapon(1302000)).thenReturn(true);
        when(inventory.getWeaponType(1302000)).thenReturn(WeaponType.SWORD2H);
        when(inventory.getEquipmentSlot(1050000)).thenReturn("MaPn");
        when(inventory.getEquipStats(1050000)).thenReturn(stats);
        when(inventory.meetsEquipRequirements(equip, Job.FIGHTER, 30, 40, 20, 4, 4, 0)).thenReturn(true);

        AgentEquipmentOptimizerHooks hooks = AgentEquipmentOptimizerHooks.from(inventory);

        assertTrue(hooks.isTwoHanded(1302000));
        assertSame(WeaponType.SWORD2H, hooks.getWeaponType(1302000));
        assertTrue(hooks.isOverall(1050000));
        assertEquals(stats, hooks.getEquipStats(1050000));
        assertTrue(hooks.meetsReqs(equip, Job.FIGHTER, 30, 40, 20, 4, 4, 0));
    }

    @Test
    void futureGatewayHooksUseAgentJobLevelFameAndMaxStats() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        Character agent = mock(Character.class);
        Equip equip = mock(Equip.class);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(35);
        when(agent.getFame()).thenReturn(7);

        AgentEquipmentOptimizerHooks hooks = AgentEquipmentOptimizerHooks.futureFrom(inventory, agent);

        assertFalse(hooks.meetsReqs(equip, Job.BEGINNER, 1, 1, 1, 1, 1, 0));
        verify(inventory).meetsEquipRequirements(
                equip,
                Job.THIEF,
                35,
                Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4,
                7);
    }
}
