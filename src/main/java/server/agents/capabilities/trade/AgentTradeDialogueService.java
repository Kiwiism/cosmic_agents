package server.agents.capabilities.trade;

import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;

public final class AgentTradeDialogueService {
    private AgentTradeDialogueService() {
    }

    public static String invitationReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeInvitationReplies());
    }

    public static String allDoneReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeAllDoneReplies());
    }

    public static String manualTradeGreeting() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.manualTradeGreetingReplies());
    }

    public static String thanksReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeThanksReplies());
    }

    public static String freebieReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeFreebieReplies());
    }

    public static String reservedForOtherReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeReservedForOtherReplies());
    }

    public static String reservedForSelfReply() {
        return AgentDialogueSelector.randomReply(AgentDialogueCatalog.tradeReservedForSelfReplies());
    }

    public static String equipsGroupMessage(String category) {
        return AgentEquipTradeGroupService.equipsGroupMessage(
                category,
                AgentTradeDialogueService::reservedForOtherReply,
                AgentTradeDialogueService::reservedForSelfReply);
    }
}
