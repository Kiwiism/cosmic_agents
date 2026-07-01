package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentDialogueSelector {
    private AgentDialogueSelector() {
    }

    public static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }
}
