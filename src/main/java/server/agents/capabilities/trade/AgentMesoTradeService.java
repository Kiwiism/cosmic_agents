package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;

public final class AgentMesoTradeService {
    private AgentMesoTradeService() {
    }

    public enum Action {
        REPLY,
        START_TRADE
    }

    public record MesoTradeStartDecision(Action action, String reply, int mesos) {
        public static MesoTradeStartDecision reply(String reply) {
            return new MesoTradeStartDecision(Action.REPLY, reply, 0);
        }

        public static MesoTradeStartDecision startTrade(int mesos) {
            return new MesoTradeStartDecision(Action.START_TRADE, null, mesos);
        }

        public boolean shouldReply() {
            return action == Action.REPLY;
        }
    }

    public static MesoTradeStartDecision decideStart(String category,
                                                     boolean ownerPresent,
                                                     boolean botBusy,
                                                     boolean ownerBusy,
                                                     int currentMesos) {
        if (!ownerPresent) {
            return MesoTradeStartDecision.reply(AgentDialogueCatalog.tradeOwnerNotFoundReply());
        }
        if (botBusy) {
            return MesoTradeStartDecision.reply(AgentDialogueCatalog.tradeBotBusyReply());
        }
        if (ownerBusy) {
            return MesoTradeStartDecision.reply(AgentDialogueCatalog.tradeOwnerBusyReply());
        }
        if (currentMesos <= 0) {
            return MesoTradeStartDecision.reply(AgentInventoryDialogueReporter.noItemsReply(category));
        }

        int requestedMesos = AgentInventoryTradePolicy.requestedTradeMesos(category);
        if (requestedMesos == 0) {
            return MesoTradeStartDecision.reply(AgentDialogueCatalog.tradeMesoInvalidReply());
        }
        if (requestedMesos > 0 && currentMesos < requestedMesos) {
            return MesoTradeStartDecision.reply(AgentInventoryTradePolicy.notEnoughMesosReply(requestedMesos, currentMesos));
        }

        return MesoTradeStartDecision.startTrade(requestedMesos > 0 ? requestedMesos : currentMesos);
    }
}
