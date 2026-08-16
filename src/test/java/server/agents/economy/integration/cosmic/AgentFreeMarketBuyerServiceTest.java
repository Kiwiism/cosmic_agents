package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFreeMarketBuyerServiceTest {
    @Test
    void refusesAdministrativeBrowsingFromOutsideFmRoom() {
        Character buyer = mock(Character.class);
        when(buyer.getMapId()).thenReturn(910000000);
        when(buyer.getClient()).thenReturn(mock(client.Client.class));
        assertThrows(IllegalStateException.class,
                () -> new AgentFreeMarketBuyerService(120).observeNearby(buyer));
    }
}
