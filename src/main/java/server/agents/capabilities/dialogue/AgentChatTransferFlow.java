package server.agents.capabilities.dialogue;

public final class AgentChatTransferFlow {
    private AgentChatTransferFlow() {
    }

    public static TransferCommand matchTransferCommand(String message) {
        String tradeCategory = AgentTradeDialogueClassifier.matchTradeCategory(message);
        if (tradeCategory != null) {
            return new TransferCommand(TransferMode.TRADE, tradeCategory);
        }

        String choiceCategory = AgentTradeDialogueClassifier.matchChoiceCategory(message);
        if (choiceCategory != null) {
            return new TransferCommand(TransferMode.CHOICE, choiceCategory);
        }

        return null;
    }

    public static boolean handleItemQuery(String message, ItemQueryCallbacks callbacks) {
        String queriedItem = AgentTradeDialogueClassifier.matchItemQuery(message);
        if (queriedItem == null) {
            return false;
        }
        callbacks.queryItem(queriedItem);
        return true;
    }

    public static TransferResultDecision transferResult(TransferCommand command, boolean hasItems, int count) {
        if (!hasItems) {
            return TransferResultDecision.reply(AgentInventoryDialogueReporter.noItemsReply(command.category()));
        }

        return switch (command.mode()) {
            case TRADE -> TransferResultDecision.startTrade();
            case CHOICE -> TransferResultDecision.promptItemChoice(command.category(), count);
        };
    }

    public static TransferResultDecision itemQueryResult(String category, int count) {
        if (count <= 0) {
            return TransferResultDecision.reply(AgentInventoryDialogueReporter.noItemsReply(category));
        }

        return TransferResultDecision.promptItemChoice(category, count);
    }

    public static boolean shouldReplyWithWeirdTransfer(TransferCommand command, String message) {
        return command.mode() == TransferMode.TRADE
                && AgentTradeDialogueClassifier.isTrashCategory(command.category())
                && message != null
                && AgentTradeDialogueClassifier.isShowJunkCommand(message);
    }

    public static String weirdTransferReply() {
        return AgentDialogueCatalog.weirdTransferReply();
    }

    public enum TransferMode {
        TRADE,
        CHOICE
    }

    public enum TransferResultAction {
        REPLY,
        START_TRADE,
        PROMPT_ITEM_CHOICE
    }

    public record TransferCommand(TransferMode mode, String category) {
    }

    public record TransferResultDecision(TransferResultAction action, String reply, String category) {
        private static TransferResultDecision reply(String reply) {
            return new TransferResultDecision(TransferResultAction.REPLY, reply, null);
        }

        private static TransferResultDecision startTrade() {
            return new TransferResultDecision(TransferResultAction.START_TRADE, null, null);
        }

        private static TransferResultDecision promptItemChoice(String category, int count) {
            return new TransferResultDecision(
                    TransferResultAction.PROMPT_ITEM_CHOICE,
                    AgentDialogueReportFormatter.dropOrTradePrompt(
                            category, count, AgentDialogueCatalog.dropOrTradePrompts()),
                    category);
        }
    }

    public interface ItemQueryCallbacks {
        void queryItem(String itemName);
    }
}
