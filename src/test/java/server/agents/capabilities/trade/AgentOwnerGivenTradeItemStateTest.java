package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOwnerGivenTradeItemStateTest {
    @Test
    void tracksIdentityBasedOwnerGivenItems() {
        AgentOwnerGivenTradeItemState state = new AgentOwnerGivenTradeItemState();
        Item item = new Item(1040000, (short) 1, (short) 1);

        assertFalse(state.hasItems());

        state.add(item);

        assertTrue(state.hasItems());
        assertSame(item, state.items().iterator().next());

        state.clear();

        assertFalse(state.hasItems());
    }
}
