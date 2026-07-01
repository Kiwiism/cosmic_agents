package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.bots.BotManager;

public final class AgentTradeDialogueService {
    private AgentTradeDialogueService() {
    }

    public static String invitationReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeInvitationReplies());
    }

    public static String allDoneReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeAllDoneReplies());
    }

    public static String manualTradeGreeting() {
        return BotManager.randomReply(AgentDialogueCatalog.manualTradeGreetingReplies());
    }

    public static String thanksReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeThanksReplies());
    }

    public static String freebieReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeFreebieReplies());
    }

    public static String reservedForOtherReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies());
    }

    public static String reservedForSelfReply() {
        return BotManager.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies());
    }

    public static String equipsGroupMessage(String category) {
        return AgentEquipTradeGroupService.equipsGroupMessage(
                category,
                AgentTradeDialogueService::reservedForOtherReply,
                AgentTradeDialogueService::reservedForSelfReply);
    }
}
