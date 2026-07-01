package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;

public final class AgentDirectItemTradeService {
    private AgentDirectItemTradeService() {
    }

    public enum Action {
        REPLY,
        RETRY,
        START_TRADE
    }

    public record DirectItemTradeDecision(Action action, String reply) {
        public static DirectItemTradeDecision reply(String reply) {
            return new DirectItemTradeDecision(Action.REPLY, reply);
        }

        public static DirectItemTradeDecision retry() {
            return new DirectItemTradeDecision(Action.RETRY, null);
        }

        public static DirectItemTradeDecision startTrade() {
            return new DirectItemTradeDecision(Action.START_TRADE, null);
        }
    }

    public static DirectItemTradeDecision decideStart(boolean recipientPresent,
                                                     boolean itemPresent,
                                                     boolean agentBusy,
                                                     boolean recipientBusy) {
        if (!recipientPresent) {
            return DirectItemTradeDecision.reply(AgentDialogueCatalog.tradeRecipientNotFoundReply());
        }
        if (!itemPresent) {
            return DirectItemTradeDecision.reply(AgentDialogueCatalog.tradeItemMissingReply());
        }
        if (agentBusy || recipientBusy) {
            return DirectItemTradeDecision.retry();
        }
        return DirectItemTradeDecision.startTrade();
    }

    public static void routeStart(DirectItemTradeDecision decision,
                                  Runnable startTrade,
                                  Runnable retry,
                                  java.util.function.Consumer<String> reply) {
        switch (decision.action()) {
            case REPLY -> reply.accept(decision.reply());
            case RETRY -> retry.run();
            case START_TRADE -> startTrade.run();
        }
    }
}
