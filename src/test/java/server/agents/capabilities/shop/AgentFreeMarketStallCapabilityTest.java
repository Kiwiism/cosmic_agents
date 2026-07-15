package server.agents.capabilities.shop;

import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentFreeMarketStallCapabilityTest {
    @Test
    void commandCarriesExplicitInventoryListings() {
        AgentFreeMarketStallService.Listing listing = new AgentFreeMarketStallService.Listing(
                InventoryType.USE, (short) 1, (short) 10, (short) 2, 0);
        AgentFreeMarketStallCapability.Command command = new AgentFreeMarketStallCapability.Command(
                910000001, "NPC-price supplies", 5140000, List.of(listing));

        assertEquals("free-market-stall", command.type());
        assertEquals(1, command.listings().size());
    }

    @Test
    void commandRejectsMapsOutsideFreeMarketRooms() {
        AgentFreeMarketStallService.Listing listing = new AgentFreeMarketStallService.Listing(
                InventoryType.ETC, (short) 1, (short) 1, (short) 1, 1);

        assertThrows(IllegalArgumentException.class, () -> new AgentFreeMarketStallCapability.Command(
                100000000, "invalid", 5140000, List.of(listing)));
    }
}
