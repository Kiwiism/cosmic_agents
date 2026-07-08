package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AgentTradeSequenceRuntimeServiceTest {
    @Test
    void announcesTradeInviteOnlyOnFirstBatchOfSequence() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, owner, null);

        when(owner.getId()).thenReturn(42);
        when(owner.getTrade()).thenReturn(null);
        when(agent.getTrade()).thenReturn(null);

        try (MockedStatic<AgentInventoryRuntime> replies = mockStatic(AgentInventoryRuntime.class);
             MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            AgentTradeSequenceRuntimeService.startTradeSequence(
                    "trash",
                    owner,
                    List.of(mock(Item.class)),
                    0,
                    false,
                    entry,
                    agent,
                    () -> {});
            AgentTradeSequenceRuntimeService.openTradeBatch(
                    entry,
                    agent,
                    List.of(mock(Item.class)),
                    0,
                    () -> {});

            replies.verify(() -> AgentInventoryRuntime.replyNow(eq(entry), anyString()), times(1));
        }
    }

    @Test
    void openTradeBatchCancelsThroughSuppliedCallbackWhenRecipientUnavailable() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AtomicBoolean cancelled = new AtomicBoolean();

        try (MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            AgentTradeSequenceRuntimeService.openTradeBatch(
                    entry,
                    agent,
                    List.of(mock(Item.class)),
                    0,
                    () -> cancelled.set(true));
        }

        assertTrue(cancelled.get());
    }
}

