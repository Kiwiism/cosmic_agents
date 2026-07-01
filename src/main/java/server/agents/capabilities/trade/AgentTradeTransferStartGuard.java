package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;

public final class AgentTradeTransferStartGuard {
    private AgentTradeTransferStartGuard() {
    }

    public static Decision evaluate(boolean ownerPresent, boolean agentBusy, boolean ownerBusy) {
        if (!ownerPresent) {
            return Decision.replyDecision(AgentDialogueCatalog.tradeOwnerNotFoundReply());
        }
        if (agentBusy) {
            return Decision.replyDecision(AgentDialogueCatalog.tradeBotBusyReply());
        }
        if (ownerBusy) {
            return Decision.replyDecision(AgentDialogueCatalog.tradeOwnerBusyReply());
        }
        return Decision.proceedDecision();
    }

    public record Decision(boolean proceed, String reply) {
        static Decision proceedDecision() {
            return new Decision(true, null);
        }

        static Decision replyDecision(String reply) {
            return new Decision(false, reply);
        }
    }
}
