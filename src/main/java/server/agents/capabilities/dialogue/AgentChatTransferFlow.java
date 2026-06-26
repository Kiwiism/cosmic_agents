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

    public enum TransferMode {
        TRADE,
        CHOICE
    }

    public record TransferCommand(TransferMode mode, String category) {
    }

    public interface ItemQueryCallbacks {
        void queryItem(String itemName);
    }
}
