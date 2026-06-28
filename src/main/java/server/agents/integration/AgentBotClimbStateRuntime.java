package server.agents.integration;

import server.bots.BotEntry;
import server.maps.Rope;

/**
 * Agent-owned adapter for temporary BotEntry-backed climb and rope state.
 */
public final class AgentBotClimbStateRuntime {
    private AgentBotClimbStateRuntime() {
    }

    public static boolean climbing(BotEntry entry) {
        return entry.climbing();
    }

    public static Rope climbRope(BotEntry entry) {
        return entry.climbRope();
    }

    public static boolean hasClimbRope(BotEntry entry) {
        return entry.climbRope() != null;
    }

    public static void setClimbingOnRope(BotEntry entry, Rope rope) {
        entry.setClimbingOnRope(rope);
    }

    public static int climbVerticalDirection(BotEntry entry) {
        return entry.climbVerticalDirection();
    }

    public static boolean hasClimbVerticalDirection(BotEntry entry) {
        return entry.climbVerticalDirection() != 0;
    }

    public static void setClimbVerticalDirection(BotEntry entry, int direction) {
        entry.setClimbVerticalDirection(direction);
    }

    public static boolean climbUpIntent(BotEntry entry) {
        return entry.climbUpIntent();
    }

    public static Rope blockedRopeGrab(BotEntry entry) {
        return entry.blockedRopeGrab();
    }

    public static boolean ropeEntryPending(BotEntry entry) {
        return entry.ropeEntryPending();
    }
}
