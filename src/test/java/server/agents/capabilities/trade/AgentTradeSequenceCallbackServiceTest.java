package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.trade.AgentTradeSequenceOrchestrator.SequenceCallbacks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeSequenceCallbackServiceTest {
    @Test
    void buildsSequenceCallbacksFromLegacyOperations() {
        Character recipient = mock(Character.class);
        Character agent = mock(Character.class);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean started = new AtomicBoolean();
        AtomicReference<Character> inviteAgent = new AtomicReference<>();
        AtomicReference<Character> inviteRecipient = new AtomicReference<>();
        AtomicReference<String> reply = new AtomicReference<>();

        SequenceCallbacks callbacks = AgentTradeSequenceCallbackService.sequenceCallbacks(
                () -> recipient,
                () -> cancelled.set(true),
                () -> started.set(true),
                (currentAgent, currentRecipient) -> {
                    inviteAgent.set(currentAgent);
                    inviteRecipient.set(currentRecipient);
                },
                () -> "trade?",
                reply::set);

        assertSame(recipient, callbacks.resolveTradeRecipient());
        callbacks.cancelUnavailableTrade();
        callbacks.startTrade();
        callbacks.inviteTrade(agent, recipient);
        callbacks.reply(callbacks.invitationReply());

        assertTrue(cancelled.get());
        assertTrue(started.get());
        assertSame(agent, inviteAgent.get());
        assertSame(recipient, inviteRecipient.get());
        assertEquals("trade?", reply.get());
    }
}
