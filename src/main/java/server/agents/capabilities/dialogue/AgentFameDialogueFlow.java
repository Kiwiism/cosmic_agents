package server.agents.capabilities.dialogue;

import client.Character;

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
            callbacks.reply(callbacks.randomFameCooldownReply());
            return;
        }
        if (status == Character.FameStatus.NOT_THIS_MONTH) {
            callbacks.reply(AgentDialogueReportFormatter.fameSamePersonReply(
                    callbacks.randomSamePersonReply(), callbacks.targetDisplayName()));
            return;
        }

        if (callbacks.gainFame()) {
            callbacks.markFameGiven();
            callbacks.reply(AgentDialogueReportFormatter.fameOkReply(
                    callbacks.randomOkReply(), callbacks.targetDisplayName()));
        } else {
            callbacks.reply(AgentDialogueCatalog.fameFailedReply());
        }
    }

    public interface FameCallbacks {
        boolean targetExists();

        boolean targetIsSelf();

        int agentLevel();

        Character.FameStatus fameStatus();

        boolean gainFame();

        void markFameGiven();

        String targetDisplayName();

        String randomOkReply();

        String randomFameCooldownReply();

        String randomSamePersonReply();

        void reply(String message);
    }
}
