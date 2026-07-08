package server.agents.runtime;

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
        return entry.ownerMotionState().lastOwnerPosition();
    }

    public static void rememberOwnerPosition(AgentRuntimeEntry entry, Point ownerPosition) {
        entry.ownerMotionState().setLastOwnerPosition(ownerPosition);
    }

    public static int observedOwnerStepX(AgentRuntimeEntry entry) {
        return entry.ownerMotionState().observedOwnerStepX();
    }

    public static int observedOwnerStepY(AgentRuntimeEntry entry) {
        return entry.ownerMotionState().observedOwnerStepY();
    }

    public static boolean observedOwnerMoved(AgentRuntimeEntry entry) {
        return entry.ownerMotionState().observedOwnerMoved();
    }

    public static int maxObservedOwnerStep(AgentRuntimeEntry entry) {
        return entry.ownerMotionState().maxObservedOwnerStep();
    }

    public static boolean ownerMostlyIdle(AgentRuntimeEntry entry) {
        return entry.ownerMotionState().ownerMostlyIdle();
    }

    public static void clearObservedOwnerStep(AgentRuntimeEntry entry) {
        entry.ownerMotionState().clearObservedOwnerStep();
    }

    public static void updateObservedOwnerStep(AgentRuntimeEntry entry, Point ownerPosition) {
        entry.ownerMotionState().updateObservedOwnerStep(ownerPosition);
    }
}
