package server.agents.integration;

import server.agents.capabilities.movement.AgentClimbState;
import server.bots.BotEntry;
import server.maps.Rope;

/**
 * Agent-owned adapter for temporary BotEntry-backed climb and rope state.
 */
public final class AgentBotClimbStateRuntime {
    private AgentBotClimbStateRuntime() {
    }

    public static boolean climbing(BotEntry entry) {
        return state(entry).climbing();
    }

    public static Rope climbRope(BotEntry entry) {
        return state(entry).climbRope();
    }

    public static boolean hasClimbRope(BotEntry entry) {
        return state(entry).hasClimbRope();
    }

    public static void setClimbingOnRope(BotEntry entry, Rope rope) {
        state(entry).setClimbingOnRope(rope);
    }

    public static int climbVerticalDirection(BotEntry entry) {
        return state(entry).verticalDirection();
    }

    public static boolean hasClimbVerticalDirection(BotEntry entry) {
        return state(entry).hasVerticalDirection();
    }

    public static void setClimbVerticalDirection(BotEntry entry, int direction) {
        state(entry).setVerticalDirection(direction);
    }

    public static boolean climbUpIntent(BotEntry entry) {
        return state(entry).climbUpIntent();
    }

    public static void setClimbUpIntent(BotEntry entry, boolean climbUpIntent) {
        state(entry).setClimbUpIntent(climbUpIntent);
    }

    public static Rope blockedRopeGrab(BotEntry entry) {
        return state(entry).blockedRopeGrab();
    }

    public static void setBlockedRopeGrab(BotEntry entry, Rope rope) {
        state(entry).setBlockedRopeGrab(rope);
    }

    public static void clearBlockedRopeGrab(BotEntry entry) {
        state(entry).clearBlockedRopeGrab();
    }

    public static int ropeGrabCooldownMs(BotEntry entry) {
        return state(entry).ropeGrabCooldownMs();
    }

    public static void setRopeGrabCooldownMs(BotEntry entry, int cooldownMs) {
        state(entry).setRopeGrabCooldownMs(cooldownMs);
    }

    public static boolean ropeEntryPending(BotEntry entry) {
        return state(entry).ropeEntryPending();
    }

    public static Rope ropeEntryRope(BotEntry entry) {
        return state(entry).ropeEntryRope();
    }

    public static int ropeEntryY(BotEntry entry) {
        return state(entry).ropeEntryY();
    }

    public static void queueRopeEntry(BotEntry entry, Rope rope, int y) {
        state(entry).queueRopeEntry(rope, y);
    }

    public static void clearRopeEntry(BotEntry entry) {
        state(entry).clearRopeEntry();
    }

    private static AgentClimbState state(BotEntry entry) {
        return entry.climbState();
    }
}
