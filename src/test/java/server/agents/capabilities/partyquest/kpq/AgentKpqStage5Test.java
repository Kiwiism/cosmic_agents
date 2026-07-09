package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKpqStage5Test {
    @Test
    void formatsNewRewardItemUsingInventoryGatewayName() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(inventory.getItemName(4001007)).thenReturn("Pass of Dimension");

        String reward = AgentKpqStage5.findNewItem(
                Map.of(4001007, 1),
                Map.of(4001007, 3),
                inventory);

        assertEquals("2x Pass of Dimension", reward);
    }

    @Test
    void fallsBackToItemIdWhenGatewayHasNoName() {
        InventoryGateway inventory = mock(InventoryGateway.class);

        String reward = AgentKpqStage5.findNewItem(
                Map.of(),
                Map.of(2000000, 1),
                inventory);

        assertEquals("item 2000000", reward);
    }
}
