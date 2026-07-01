package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;
import server.agents.integration.AgentBotInventoryRuntime;
import server.bots.BotEntry;

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
        BotEntry entry = new BotEntry(agent, owner, null);

        when(owner.getId()).thenReturn(42);
        when(owner.getTrade()).thenReturn(null);
        when(agent.getTrade()).thenReturn(null);

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class);
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

            replies.verify(() -> AgentBotInventoryRuntime.replyNow(eq(entry), anyString()), times(1));
        }
    }

    @Test
    void openTradeBatchCancelsThroughSuppliedCallbackWhenRecipientUnavailable() {
        Character agent = mock(Character.class);
        BotEntry entry = new BotEntry(agent, null, null);
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
