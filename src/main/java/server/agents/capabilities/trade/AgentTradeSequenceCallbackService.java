package server.agents.capabilities.trade;

import client.Character;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentTradeSequenceCallbackService {
    private AgentTradeSequenceCallbackService() {
    }

    public static AgentTradeSequenceOrchestrator.SequenceCallbacks sequenceCallbacks(
            Supplier<Character> resolveTradeRecipient,
            Runnable cancelUnavailableTrade,
            Runnable startTrade,
            BiConsumer<Character, Character> inviteTrade,
            Supplier<String> invitationReply,
            Consumer<String> reply) {
        return AgentTradeSequenceOrchestrator.SequenceCallbacks.of(
                resolveTradeRecipient,
                cancelUnavailableTrade,
                startTrade,
                inviteTrade,
                invitationReply,
                reply);
    }
}
