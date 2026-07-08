package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentSupplyShareTradeServiceTest {
    @Test
    void shouldIgnoreEmptyPotionShareTransfer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        AgentSupplyShareTradeService.startPotShareTransfer(
                List.of(), mock(Character.class), entry, mock(Character.class), 10);

        assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
    }

    @Test
    void shouldQueueRetryWhenShareTransferIsAlreadyActive() {
        Character recipient = mock(Character.class);
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, recipient, null);
        AgentPendingTradeStateRuntime.setCategory(entry, "pot_share");

        AgentSupplyShareTradeService.startAmmoShareTransfer(
                List.of(mock(Item.class)), recipient, entry, agent, 10);

        assertTrue(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
        assertNotNull(AgentPendingTradeStateRuntime.takeRetry(entry));
    }
}

