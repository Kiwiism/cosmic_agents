package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTradeSequenceOrchestratorTest {
    @Test
    void startTradeSequenceMissingRecipientRepliesWithoutOpening() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        TraceCallbacks callbacks = new TraceCallbacks();

        try (MockedStatic<AgentBotInventoryRuntime> replies = mockStatic(AgentBotInventoryRuntime.class)) {
            AgentTradeSequenceOrchestrator.startTradeSequence(
                    "scrolls",
                    null,
                    List.of(item(2040000)),
                    0,
                    true,
                    entry,
                    mock(Character.class),
                    callbacks);

            replies.verify(() -> AgentBotInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeRecipientNotFoundReply()));
            assertFalse(callbacks.started.get());
            assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        }
    }

    @Test
    void startTradeSequenceInitializesAndOpensBatch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Character recipient = mock(Character.class);
        Item item = item(2040000);
        TraceCallbacks callbacks = new TraceCallbacks();
        callbacks.recipient.set(recipient);
        when(recipient.getId()).thenReturn(91);

        AgentTradeSequenceOrchestrator.startTradeSequence(
                "scrolls",
                recipient,
                List.of(item),
                250,
                false,
                entry,
                agent,
                callbacks);

        assertEquals("scrolls", AgentPendingTradeStateRuntime.category(entry));
        assertEquals(91, AgentPendingTradeStateRuntime.recipientId(entry));
        assertEquals(false, AgentPendingTradeStateRuntime.singleBatch(entry));
        assertEquals(List.of(item), AgentPendingTradeStateRuntime.items(entry));
        assertEquals(250, AgentPendingTradeStateRuntime.meso(entry));
        assertTrue(callbacks.started.get());
        assertSame(agent, callbacks.inviteAgent.get());
        assertSame(recipient, callbacks.inviteRecipient.get());
        assertEquals("invite", callbacks.reply.get());
    }

    @Test
    void openTradeBatchCancelsWhenResolvedRecipientBusy() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        TraceCallbacks callbacks = new TraceCallbacks();
        Character recipient = mock(Character.class);
        callbacks.recipient.set(recipient);
        when(recipient.getTrade()).thenReturn(mock(server.Trade.class));

        AgentTradeSequenceOrchestrator.openTradeBatch(
                entry,
                mock(Character.class),
                List.of(item(2000000)),
                0,
                callbacks);

        assertTrue(callbacks.cancelled.get());
        assertFalse(callbacks.started.get());
    }

    private static Item item(int itemId) {
        return new Item(itemId, (short) 1, (short) 1);
    }

    private static final class TraceCallbacks implements AgentTradeSequenceOrchestrator.SequenceCallbacks {
        final AtomicReference<Character> recipient = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicReference<Character> inviteAgent = new AtomicReference<>();
        final AtomicReference<Character> inviteRecipient = new AtomicReference<>();
        final AtomicReference<String> reply = new AtomicReference<>();

        @Override
        public Character resolveTradeRecipient() {
            return recipient.get();
        }

        @Override
        public void cancelUnavailableTrade() {
            cancelled.set(true);
        }

        @Override
        public void startTrade() {
            started.set(true);
        }

        @Override
        public void inviteTrade(Character agent, Character recipient) {
            inviteAgent.set(agent);
            inviteRecipient.set(recipient);
        }

        @Override
        public String invitationReply() {
            return "invite";
        }

        @Override
        public void reply(String message) {
            reply.set(message);
        }
    }
}
