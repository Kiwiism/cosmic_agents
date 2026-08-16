package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmicNpcResourceProcurementTest {
    @Test
    void rechargesOwnedDepletedProjectileBeforeBuyingAnotherSet() {
        CosmicAgentNeedReader needs = mock(CosmicAgentNeedReader.class);
        RemoteNpcCommerceService commerce = mock(RemoteNpcCommerceService.class);
        Character agent = mock(Character.class); Inventory use = mock(Inventory.class);
        Item stars = mock(Item.class);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.countById(2070000)).thenReturn(100, 1000);
        when(use.listById(2070000)).thenReturn(List.of(stars));
        when(stars.getQuantity()).thenReturn((short) 100); when(stars.getPosition()).thenReturn((short) 3);
        when(needs.missingNpcResources(agent, profile())).thenReturn(List.of(
                new CosmicAgentNeedReader.ResourceProcurement(1001100, 2070000, 400)));
        when(commerce.recharge(agent, 1001100, (short) 3)).thenReturn(
                new RemoteNpcCommerceService.Receipt(true, "SUCCESS", 1001100, 100000000,
                        -500, "REMOTE_FROM_FREE_MARKET"));
        CosmicNpcResourceProcurement procurement = new CosmicNpcResourceProcurement(
                needs, commerce, (character, itemId) -> 1000);

        Optional<AutonomousFreeMarketBehavior.ResourceProcurement.Result> result =
                procurement.buyNext(agent, profile(), Set.of());

        assertEquals("RECHARGE", result.orElseThrow().commerceAction());
        assertEquals(900, result.orElseThrow().quantity());
        verify(commerce).recharge(agent, 1001100, (short) 3);
        verify(commerce, never()).buy(any(), anyInt(), anyInt(), anyShort());
    }

    private static server.agents.economy.session.CommerceParticipant profile() {
        return new server.agents.economy.session.CommerceParticipant("agent", "thief", .5,
                .5, .5, .5, .5, .5, 24, .5, .5);
    }
}
