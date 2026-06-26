package server.agents.capabilities.dialogue;

import client.Job;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    public static String jobChangeReply(Job job) {
        return AgentDialogueReportFormatter.jobChangeReply(
                randomReply(AgentDialogueCatalog.jobChangeReplyTemplates()),
                AgentDialogueReportFormatter.jobDisplayName(job));
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface JobAdvancementCallbacks {
        void advanceTo(Job job);
    }
}
