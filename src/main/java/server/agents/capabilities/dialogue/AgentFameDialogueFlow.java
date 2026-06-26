package server.agents.capabilities.dialogue;

import client.Character;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentFameDialogueFlow {
    private AgentFameDialogueFlow() {
    }

    public static void handle(String targetName, FameCallbacks callbacks) {
        if (!callbacks.targetExists()) {
            callbacks.reply(AgentDialogueCatalog.fameTargetNotFoundReply(targetName));
            return;
        }
        if (callbacks.targetIsSelf()) {
            callbacks.reply(AgentDialogueCatalog.fameSelfReply());
            return;
        }
        if (callbacks.agentLevel() < 15) {
            callbacks.reply(AgentDialogueCatalog.fameTooLowLevelReply());
            return;
        }

        Character.FameStatus status = callbacks.fameStatus();
        if (status == Character.FameStatus.NOT_TODAY) {
            callbacks.reply(randomReply(AgentDialogueCatalog.fameCooldownReplies()));
            return;
        }
        if (status == Character.FameStatus.NOT_THIS_MONTH) {
            callbacks.reply(AgentDialogueReportFormatter.fameSamePersonReply(
                    randomReply(AgentDialogueCatalog.fameSamePersonReplies()), callbacks.targetDisplayName()));
            return;
        }

        if (callbacks.gainFame()) {
            callbacks.markFameGiven();
            callbacks.reply(AgentDialogueReportFormatter.fameOkReply(
                    randomReply(AgentDialogueCatalog.fameOkReplies()), callbacks.targetDisplayName()));
        } else {
            callbacks.reply(AgentDialogueCatalog.fameFailedReply());
        }
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface FameCallbacks {
        boolean targetExists();

        boolean targetIsSelf();

        int agentLevel();

        Character.FameStatus fameStatus();

        boolean gainFame();

        void markFameGiven();

        String targetDisplayName();

        void reply(String message);
    }
}
