package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeRecommendationServiceTest {
    @Test
    void noOwnerReturnsEmptyRecommendationsWithoutCallingCollector() {
        Character agent = mock(Character.class);
        AtomicBoolean called = new AtomicBoolean();

        List<Item> items = AgentTradeRecommendationService.recommendedItems(
                null,
                agent,
                (owner, currentAgent) -> {
                    called.set(true);
                    return List.of(mock(Item.class));
                });

        assertEquals(List.of(), items);
        assertFalse(called.get());
    }

    @Test
    void ownerUsesSuppliedRecommendationCollector() {
        Character owner = mock(Character.class);
        Character agent = mock(Character.class);
        Item item = mock(Item.class);
        AtomicBoolean called = new AtomicBoolean();

        List<Item> items = AgentTradeRecommendationService.recommendedItems(
                owner,
                agent,
                (currentOwner, currentAgent) -> {
                    called.set(true);
                    assertSame(owner, currentOwner);
                    assertSame(agent, currentAgent);
                    return List.of(item);
                });

        assertEquals(List.of(item), items);
        assertTrue(called.get());
    }
}
