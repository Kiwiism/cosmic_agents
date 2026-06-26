package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatUtilityFlow {
    private AgentChatUtilityFlow() {
    }

    public static boolean handle(String message, UtilityCallbacks callbacks) {
        if (AgentUtilityDialogueClassifier.isTradeInviteCommand(message)) {
            callbacks.tradeInvite();
            return true;
        }
        if (AgentUtilityDialogueClassifier.isSellTrashCommand(message)) {
            callbacks.sellTrash();
            return true;
        }
        if (AgentUtilityDialogueClassifier.isMakeCrystalsCommand(message)) {
            callbacks.makeCrystals();
            return true;
        }
        if (AgentUtilityDialogueClassifier.isDisassembleTrashCommand(message)) {
            callbacks.disassembleTrash();
            return true;
        }
        return false;
    }

    public static String tradeInviteReply() {
        return randomReply(AgentDialogueCatalog.tradeInviteReplies());
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface UtilityCallbacks {
        void tradeInvite();

        void sellTrash();

        void makeCrystals();

        void disassembleTrash();
    }
}
