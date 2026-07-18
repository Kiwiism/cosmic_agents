package server.agents.capabilities.follow;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentFollowTargetMotionRuntime {
    private AgentFollowTargetMotionRuntime() {
    }

    public static Point lastPosition(AgentRuntimeEntry entry) { return entry.followTargetMotionState().lastPosition(); }
    public static void remember(AgentRuntimeEntry entry, Point position) { entry.followTargetMotionState().remember(position); }
    public static int observedStepX(AgentRuntimeEntry entry) { return entry.followTargetMotionState().observedStepX(); }
    public static int observedStepY(AgentRuntimeEntry entry) { return entry.followTargetMotionState().observedStepY(); }
    public static boolean observedMovement(AgentRuntimeEntry entry) { return entry.followTargetMotionState().observedMovement(); }
    public static int maximumObservedStep(AgentRuntimeEntry entry) { return entry.followTargetMotionState().maximumObservedStep(); }
    public static boolean mostlyIdle(AgentRuntimeEntry entry) { return entry.followTargetMotionState().mostlyIdle(); }
    public static void clearObservedStep(AgentRuntimeEntry entry) { entry.followTargetMotionState().clearObservedStep(); }
    public static void updateObservedStep(AgentRuntimeEntry entry, Point position) {
        entry.followTargetMotionState().updateObservedStep(position);
    }
}
