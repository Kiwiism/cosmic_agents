package server.agents.capabilities.dialogue;

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
