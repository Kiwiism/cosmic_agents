package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentPendingOfferChatRouteServiceTest {
    @Test
    void returnsFalseWhenNoEntryHasPendingOffer() {
        Character speaker = mock(Character.class);
        BotEntry entry = new BotEntry(mock(Character.class), speaker, null);

        boolean handled = AgentPendingOfferChatRouteService.handlePendingOfferResponse(
                List.of(List.of(entry)),
                speaker,
                "hello");

        assertFalse(handled);
    }
}
