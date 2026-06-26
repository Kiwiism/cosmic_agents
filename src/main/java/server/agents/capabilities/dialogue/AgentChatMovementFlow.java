package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatMovementFlow {
    private AgentChatMovementFlow() {
    }

    public static boolean handle(String message, MovementCallbacks callbacks) {
        if (AgentChatCommandClassifier.isFarmHereCommand(message)) {
            return callbacks.farmHere();
        }
        if (AgentChatCommandClassifier.isPatrolCommand(message)) {
            return callbacks.patrol();
        }
        if (AgentChatCommandClassifier.isMoveHereCommand(message)) {
            return callbacks.moveHere();
        }
        if (AgentChatCommandClassifier.isFollowCommand(message)) {
            callbacks.follow();
            return true;
        }
        if (AgentChatCommandClassifier.isGrindCommand(message)) {
            callbacks.grind();
            return true;
        }
        if (AgentChatCommandClassifier.isStopCommand(message)) {
            callbacks.stop();
            return true;
        }
        if (AgentChatCommandClassifier.isFidgetCommand(message)) {
            callbacks.fidget();
            return true;
        }
        if (AgentSocialDialogueClassifier.isGreeting(message)) {
            callbacks.greeting();
            return true;
        }
        return false;
    }

    public static String moveHereReply() {
        return randomReply(AgentDialogueCatalog.moveHereReplies());
    }

    public static String followReply() {
        return randomReply(AgentDialogueCatalog.followReplies());
    }

    public static String stopReply() {
        return randomReply(AgentDialogueCatalog.stopReplies());
    }

    public static String greetingReply() {
        return randomReply(AgentDialogueCatalog.greetingReplies());
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface MovementCallbacks {
        boolean farmHere();

        boolean patrol();

        boolean moveHere();

        void follow();

        void grind();

        void stop();

        void fidget();

        void greeting();
    }
}
