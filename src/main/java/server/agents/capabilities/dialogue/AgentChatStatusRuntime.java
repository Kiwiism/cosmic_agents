package server.agents.capabilities.dialogue;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatStatusRuntime {
    private static final int[] FIDGET_EXPRESSIONS = {2, 3, 5, 6, 7};

    private AgentChatStatusRuntime() {
    }

    public static void markActive(StatusState state, Point ownerPosition, long nowMs) {
        state.setOwnerWasAfk(false);
        state.setOwnerAfkSinceMs(nowMs);
        state.setOwnerAfkPosition(ownerPosition != null ? new Point(ownerPosition) : null);
    }

    public static boolean isOwnerIdle(StatusState state) {
        return state.ownerWasAfk();
    }

    public static int randomFidgetExpression() {
        return FIDGET_EXPRESSIONS[ThreadLocalRandom.current().nextInt(FIDGET_EXPRESSIONS.length)];
    }

    public interface StatusState {
        void setOwnerAfkPosition(Point position);

        void setOwnerAfkSinceMs(long sinceMs);

        boolean ownerWasAfk();

        void setOwnerWasAfk(boolean wasAfk);
    }
}
