package server.agents.capabilities.dialogue;

import client.Job;

public final class AgentChatJobAdvancementFlow {
    private AgentChatJobAdvancementFlow() {
    }

    public static boolean handle(String message, Job currentJob, int level, JobAdvancementCallbacks callbacks) {
        if (!AgentBuildDialogueClassifier.isJobSelectionCandidate(message)) {
            return false;
        }

        Job advancement = AgentBuildDialogueClassifier.resolveJobChange(currentJob, level, message.toLowerCase());
        if (advancement == null) {
            return false;
        }

        callbacks.advanceTo(advancement);
        return true;
    }

    public interface JobAdvancementCallbacks {
        void advanceTo(Job job);
    }
}
