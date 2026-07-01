package server.agents.runtime;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import testutil.Items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
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
}
