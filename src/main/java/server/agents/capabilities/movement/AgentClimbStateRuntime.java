package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed climb and rope state.
 */
public final class AgentClimbStateRuntime {
    private AgentClimbStateRuntime() {
    }

    public static boolean climbing(AgentRuntimeEntry entry) {
        return state(entry).climbing();
    }

    public static Rope climbRope(AgentRuntimeEntry entry) {
        return state(entry).climbRope();
    }

    public static boolean hasClimbRope(AgentRuntimeEntry entry) {
        return state(entry).hasClimbRope();
    }

    public static void setClimbingOnRope(AgentRuntimeEntry entry, Rope rope) {
        state(entry).setClimbingOnRope(rope);
    }

    public static int climbVerticalDirection(AgentRuntimeEntry entry) {
        return state(entry).verticalDirection();
    }

    public static boolean hasClimbVerticalDirection(AgentRuntimeEntry entry) {
        return state(entry).hasVerticalDirection();
    }

    public static void setClimbVerticalDirection(AgentRuntimeEntry entry, int direction) {
        state(entry).setVerticalDirection(direction);
    }

    public static boolean climbUpIntent(AgentRuntimeEntry entry) {
        return state(entry).climbUpIntent();
    }

    public static void setClimbUpIntent(AgentRuntimeEntry entry, boolean climbUpIntent) {
        state(entry).setClimbUpIntent(climbUpIntent);
    }

    public static Rope blockedRopeGrab(AgentRuntimeEntry entry) {
        return state(entry).blockedRopeGrab();
    }

    public static void setBlockedRopeGrab(AgentRuntimeEntry entry, Rope rope) {
        state(entry).setBlockedRopeGrab(rope);
    }

    public static void clearBlockedRopeGrab(AgentRuntimeEntry entry) {
        state(entry).clearBlockedRopeGrab();
    }

    public static int ropeGrabCooldownMs(AgentRuntimeEntry entry) {
        return state(entry).ropeGrabCooldownMs();
    }

    public static void setRopeGrabCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
        state(entry).setRopeGrabCooldownMs(cooldownMs);
    }

    public static boolean ropeEntryPending(AgentRuntimeEntry entry) {
        return state(entry).ropeEntryPending();
    }

    public static Rope ropeEntryRope(AgentRuntimeEntry entry) {
        return state(entry).ropeEntryRope();
    }

    public static int ropeEntryY(AgentRuntimeEntry entry) {
        return state(entry).ropeEntryY();
    }

    public static int ropeEntryDirection(AgentRuntimeEntry entry) {
        return state(entry).ropeEntryDirection();
    }

    public static void queueRopeEntry(AgentRuntimeEntry entry, Rope rope, int y, int direction) {
        state(entry).queueRopeEntry(rope, y, direction);
    }

    public static void clearRopeEntry(AgentRuntimeEntry entry) {
        state(entry).clearRopeEntry();
    }

    private static AgentClimbState state(AgentRuntimeEntry entry) {
        return entry.climbState();
    }
}
