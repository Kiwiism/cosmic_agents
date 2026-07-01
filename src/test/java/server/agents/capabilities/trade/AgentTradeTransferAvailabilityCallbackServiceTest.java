package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.trade.AgentTradeTransferAvailabilityService.TransferAvailabilityCallbacks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentTradeTransferAvailabilityCallbackServiceTest {
    @Test
    void buildsTransferAvailabilityCallbacksFromLegacyOperations() {
        List<Item> items = List.of(mock(Item.class));

        TransferAvailabilityCallbacks callbacks =
                AgentTradeTransferAvailabilityCallbackService.transferAvailabilityCallbacks(
                        fragment -> fragment.length(),
                        fragment -> fragment.length() + 1,
                        () -> items);

        assertEquals(3, callbacks.countNamedItems("hat"));
        assertEquals(4, callbacks.countEquippedSlotItems("hat"));
        assertSame(items, callbacks.collectItems());
    }
}
