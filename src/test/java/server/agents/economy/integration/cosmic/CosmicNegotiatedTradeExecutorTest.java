package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.social.TradeOffer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class CosmicNegotiatedTradeExecutorTest {
    @Test
    void refusesTradeWhenAgentsHaveNotWalkedTogether() {
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        when(first.getClient()).thenReturn(mock(client.Client.class));
        when(second.getClient()).thenReturn(mock(client.Client.class));
        when(first.getMap()).thenReturn(mock(server.maps.MapleMap.class));
        when(second.getMap()).thenReturn(mock(server.maps.MapleMap.class));
        var executor = new CosmicNegotiatedTradeExecutor(
                id -> "a".equals(id) ? first : second, 120);

        var result = executor.execute("deal", "a", new TradeOffer(1, Map.of()),
                "b", new TradeOffer(0, Map.of(4000000, 1)));

        assertFalse(result.succeeded());
        verify(first, never()).setTrade(any());
    }
}
