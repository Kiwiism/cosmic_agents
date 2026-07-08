package server.agents.capabilities.trade;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentTradeConfirmWaitService {
    private static final int CONFIRM_TIMEOUT_MS = 60_000;

    private AgentTradeConfirmWaitService() {
    }

    public static boolean tickWaitingForConfirmation(AgentRuntimeEntry entry,
                                                     Character agent,
                                                     Trade trade,
                                                     int tickMs,
                                                     Supplier<Character> recipientResolver,
                                                     Predicate<Character> botRecipient,
                                                     Runnable completeTrade,
                                                     Runnable resetTradeState) {
        AgentPendingTradeStateRuntime.addTimerMs(entry, tickMs);
        Character recipient = recipientResolver.get();
        boolean recipientIsBot = recipient != null && botRecipient.test(recipient);
        if (recipientIsBot || trade.isPartnerConfirmed()) {
            completeTrade.run();
            AgentPendingTradeStateRuntime.markBotDone(entry);
            AgentPendingTradeStateRuntime.clearTimer(entry);
        } else if (AgentPendingTradeStateRuntime.timerMs(entry) > CONFIRM_TIMEOUT_MS) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeConfirmTimeoutReply());
            Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
            resetTradeState.run();
        }
        return true;
    }
}
