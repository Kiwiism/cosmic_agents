package server.agents.capabilities.follow;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed leader/owner motion
 * observation state.
 */
public final class AgentOwnerMotionStateRuntime {
    private AgentOwnerMotionStateRuntime() {
    }

    public static Point lastOwnerPosition(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.lastPosition(entry);
    }

    public static void rememberOwnerPosition(AgentRuntimeEntry entry, Point ownerPosition) {
        AgentFollowTargetMotionRuntime.remember(entry, ownerPosition);
    }

    public static int observedOwnerStepX(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.observedStepX(entry);
    }

    public static int observedOwnerStepY(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.observedStepY(entry);
    }

    public static boolean observedOwnerMoved(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.observedMovement(entry);
    }

    public static int maxObservedOwnerStep(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.maximumObservedStep(entry);
    }

    public static boolean ownerMostlyIdle(AgentRuntimeEntry entry) {
        return AgentFollowTargetMotionRuntime.mostlyIdle(entry);
    }

    public static void clearObservedOwnerStep(AgentRuntimeEntry entry) {
        AgentFollowTargetMotionRuntime.clearObservedStep(entry);
    }

    public static void updateObservedOwnerStep(AgentRuntimeEntry entry, Point ownerPosition) {
        AgentFollowTargetMotionRuntime.updateObservedStep(entry, ownerPosition);
    }
}
