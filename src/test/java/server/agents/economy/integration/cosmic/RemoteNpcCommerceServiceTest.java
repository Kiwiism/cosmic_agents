package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.*;
import server.agents.integration.ShopGateway;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RemoteNpcCommerceServiceTest {
    @Test
    void refusesInvisibleRemoteAccessOutsideFreeMarket() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(100000000);
        when(agent.getClient()).thenReturn(mock(client.Client.class));
        EconomyCatalog catalog = mock(EconomyCatalog.class);
        ShopGateway shops = mock(ShopGateway.class);

        assertThrows(IllegalStateException.class,
                () -> new RemoteNpcCommerceService(catalog, shops).buy(agent, 1001000, 2000000, (short) 1));
        verifyNoInteractions(catalog, shops);
    }

    @Test
    void refusesCatalogWithoutOriginalNpcMapEvidence() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(910000000);
        when(agent.getClient()).thenReturn(mock(client.Client.class));
        EconomyCatalog catalog = mock(EconomyCatalog.class);
        when(catalog.npcShop(1001000)).thenReturn(Optional.of(
                new NpcShopFact(1001000, 1001000, null, List.of())));

        assertThrows(IllegalStateException.class, () -> new RemoteNpcCommerceService(catalog,
                mock(ShopGateway.class)).buy(agent, 1001000, 2000000, (short) 1));
    }
}
