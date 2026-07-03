package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentServiceTest {
    @Test
    void shouldExposeAgentOwnedWeaponCompatibilityBoundary() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.BOWMAN);

        assertTrue(AgentEquipmentService.isWeaponCompatible(agent, WeaponType.BOW));
        assertTrue(AgentEquipmentService.isWeaponCompatible(agent, WeaponType.CROSSBOW));
        assertFalse(AgentEquipmentService.isWeaponCompatible(agent, WeaponType.CLAW));
    }

    @Test
    void shouldExposeAgentOwnedMageJobBoundary() {
        assertTrue(AgentEquipmentService.isMageJob(Job.MAGICIAN));
        assertTrue(AgentEquipmentService.isMageJob(Job.BISHOP));
        assertFalse(AgentEquipmentService.isMageJob(Job.ASSASSIN));
    }

    @Test
    void shouldExposeAgentOwnedReserveBoundaryForNonEquipItems() {
        Character agent = mock(Character.class);
        Item item = mock(Item.class);

        assertFalse(AgentEquipmentService.shouldReserveOwnedItem(agent, item));
    }
}
