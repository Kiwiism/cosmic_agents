package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPendingOfferChatRouteServiceTest {
    @Test
    void delayedJudgmentOnlyMatchesTheOriginalOffer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Item original = mock(Item.class);
        AgentOfferStateRuntime.setPendingLootOffer(entry, original, 1, 1000L, false);
        assertTrue(AgentPendingOfferChatRouteService.matchesJudgedOffer(entry, original, 1000L, false));

        AgentOfferStateRuntime.setPendingLootOffer(entry, mock(Item.class), 1, 1000L, false);
        assertFalse(AgentPendingOfferChatRouteService.matchesJudgedOffer(entry, original, 1000L, false));

        AgentOfferStateRuntime.setPendingLootOffer(entry, original, 1, 2000L, false);
        assertFalse(AgentPendingOfferChatRouteService.matchesJudgedOffer(entry, original, 1000L, false));

        AgentOfferStateRuntime.setPendingLootOffer(entry, original, 1, 1000L, true);
        assertFalse(AgentPendingOfferChatRouteService.matchesJudgedOffer(entry, original, 1000L, false));

        AgentOfferStateRuntime.clearPendingOffer(entry);
        assertFalse(AgentPendingOfferChatRouteService.matchesJudgedOffer(entry, original, 1000L, false));
    }

    @Test
    void returnsFalseWhenNoEntryHasPendingOffer() {
        Character speaker = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), speaker, null);

        boolean handled = AgentPendingOfferChatRouteService.handlePendingOfferResponse(
                List.of(List.of(entry)),
                speaker,
                "hello");

        assertFalse(handled);
    }
}
