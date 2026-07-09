package server.agents.runtime;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.integration.InventoryGateway;
import testutil.Items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReturnScrollServiceTest {
    @Test
    void returnsFalseWhenUseInventoryIsMissing() {
        Character agent = mock(Character.class);
        when(agent.getInventory(InventoryType.USE)).thenReturn(null);

        assertFalse(AgentReturnScrollService.tryUseNearestTownReturnScroll(agent));
    }

    @Test
    void returnsFalseWhenUseInventoryHasNoReturnScroll() {
        Character agent = mock(Character.class);
        Inventory use = new Inventory(agent, InventoryType.USE, (byte) 8);
        use.addItem(Items.itemWithQuantity(2000000, 1));
        use.addItem(Items.itemWithQuantity(2030001, 1));
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);

        assertFalse(AgentReturnScrollService.tryUseNearestTownReturnScroll(agent));
    }

    @Test
    void appliesReturnScrollAndRemovesOneScrollThroughGateway() {
        Character agent = mock(Character.class);
        Inventory use = new Inventory(agent, InventoryType.USE, (byte) 8);
        use.addItem(Items.itemWithQuantity(2030000, 2));
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        InventoryGateway inventoryGateway = mock(InventoryGateway.class);
        StatEffect effect = mock(StatEffect.class);
        when(inventoryGateway.getItemEffect(2030000)).thenReturn(effect);
        when(effect.applyTo(agent)).thenReturn(true);

        assertTrue(AgentReturnScrollService.tryUseNearestTownReturnScroll(agent, inventoryGateway));

        verify(inventoryGateway).removeFromSlot(agent, InventoryType.USE, (short) 1, (short) 1, false);
    }
}
