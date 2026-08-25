package server.agents.economy.integration.cosmic;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import server.Storage;
import server.agents.economy.market.FreeMarketPhysicalGateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CosmicFreeMarketStorageAccessTest {
    @Test
    void movesToEntranceBeforeOpeningRealAccountStorage() {
        FreeMarketPhysicalGateway physical = mock(FreeMarketPhysicalGateway.class);
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        Storage storage = mock(Storage.class);
        when(agent.getClient()).thenReturn(client);
        when(physical.requestEntrance(agent)).thenReturn(
                FreeMarketPhysicalGateway.ActionStatus.ASSIGNED,
                FreeMarketPhysicalGateway.ActionStatus.ARRIVED);
        when(agent.getMapId()).thenReturn(910000000);
        when(agent.getStorage()).thenReturn(storage);
        when(storage.isOpen()).thenReturn(true);
        CosmicFreeMarketStorageAccess access = new CosmicFreeMarketStorageAccess(
                physical, 910000000, 1002005);

        assertEquals(CosmicFreeMarketStorageAccess.Status.MOVING_TO_ENTRANCE,
                access.request(agent).status());
        assertEquals(CosmicFreeMarketStorageAccess.Status.OPENED,
                access.request(agent).status());
        verify(storage).sendStorage(client, 1002005);
    }
}
