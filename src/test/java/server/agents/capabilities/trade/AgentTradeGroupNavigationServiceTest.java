package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeGroupNavigationServiceTest {
    @Test
    void selectsNextEquipsGroupThroughAgentGroupPolicy() {
        Item other = mock(Item.class);
        Item self = mock(Item.class);
        AtomicBoolean supplied = new AtomicBoolean();

        String next = AgentTradeGroupNavigationService.nextEquipsGroup(
                "equips:reserved_for_other",
                () -> {
                    supplied.set(true);
                    return new AgentEquipTradeGroups(List.of(), List.of(other), List.of(self));
                });

        assertEquals("equips:reserved_for_self", next);
        assertTrue(supplied.get());
        assertNull(AgentTradeGroupNavigationService.nextEquipsGroup(
                "equips:reserved_for_self",
                () -> new AgentEquipTradeGroups(List.of(), List.of(other), List.of(self))));
    }

    @Test
    void selectsNextAmmoGroupThroughAgentAmmoPolicy() {
        Item nonOwn = mock(Item.class);
        Item own = mock(Item.class);
        AtomicBoolean supplied = new AtomicBoolean();

        String next = AgentTradeGroupNavigationService.nextAmmoGroup(
                "ammo:non_own",
                () -> {
                    supplied.set(true);
                    return new AmmoTradeGroups(List.of(nonOwn), List.of(own));
                });

        assertEquals("ammo:own", next);
        assertTrue(supplied.get());
        assertNull(AgentTradeGroupNavigationService.nextAmmoGroup(
                "ammo:own",
                () -> new AmmoTradeGroups(List.of(nonOwn), List.of(own))));
    }
}
