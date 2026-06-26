package server.agents.capabilities.dialogue;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatWelcomeBackFlow {
    private static final long AFK_THRESHOLD_MS = 5 * 60_000L;

    private AgentChatWelcomeBackFlow() {
    }

    public static void tickAfkCheck(AfkState state, Point ownerPosition, long nowMs, WelcomeBackCallbacks callbacks) {
        if (state.ownerAfkPosition() == null) {
            state.setOwnerAfkPosition(ownerPosition);
            state.setOwnerAfkSinceMs(nowMs);
            return;
        }

        if (!ownerPosition.equals(state.ownerAfkPosition())) {
            if (state.ownerWasAfk()) {
                state.setOwnerWasAfk(false);
                callbacks.ownerReturned();
            }
            state.setOwnerAfkPosition(ownerPosition);
            state.setOwnerAfkSinceMs(nowMs);
        } else if (!state.ownerWasAfk() && (nowMs - state.ownerAfkSinceMs()) >= AFK_THRESHOLD_MS) {
            state.setOwnerWasAfk(true);
        }
    }

    public static String welcomeBackReply() {
        return randomReply(AgentDialogueCatalog.welcomeBackReplies());
    }

    public static String welcomeBackOfflinePartyReply(String mapName) {
        return AgentDialogueReportFormatter.welcomeBackOfflineReply(
                randomReply(AgentDialogueCatalog.welcomeBackOfflinePartyTemplates()),
                mapName);
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }

    public interface AfkState {
        Point ownerAfkPosition();

        void setOwnerAfkPosition(Point position);

        long ownerAfkSinceMs();

        void setOwnerAfkSinceMs(long sinceMs);

        boolean ownerWasAfk();

        void setOwnerWasAfk(boolean wasAfk);
    }

    public interface WelcomeBackCallbacks {
        void ownerReturned();
    }
}
