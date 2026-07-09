package server.agents.capabilities.trade;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.integration.AgentTradeGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentTradeConfirmWaitService {
    private static final int CONFIRM_TIMEOUT_MS = 60_000;

    private AgentTradeConfirmWaitService() {
    }

    public static boolean tickWaitingForConfirmation(AgentRuntimeEntry entry,
                                                     Character agent,
                                                     BooleanSupplier partnerConfirmed,
                                                     int tickMs,
                                                     Supplier<Character> recipientResolver,
                                                     Predicate<Character> botRecipient,
                                                     Runnable completeTrade,
                                                     Runnable resetTradeState) {
        AgentPendingTradeStateRuntime.addTimerMs(entry, tickMs);
        Character recipient = recipientResolver.get();
        boolean recipientIsBot = recipient != null && botRecipient.test(recipient);
        if (recipientIsBot || partnerConfirmed.getAsBoolean()) {
            completeTrade.run();
            AgentPendingTradeStateRuntime.markBotDone(entry);
            AgentPendingTradeStateRuntime.clearTimer(entry);
        } else if (AgentPendingTradeStateRuntime.timerMs(entry) > CONFIRM_TIMEOUT_MS) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeConfirmTimeoutReply());
            AgentTradeGatewayRuntime.trade().cancelNoResponse(agent);
            resetTradeState.run();
        }
        return true;
    }
}
