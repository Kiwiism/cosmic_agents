package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed movement broadcast cache state.
 */
public final class AgentMovementBroadcastStateRuntime {
    private AgentMovementBroadcastStateRuntime() {
    }

    public static void invalidate(AgentRuntimeEntry entry) {
        entry.movementBroadcastState().setValid(false);
    }

    public static void beginTick(AgentRuntimeEntry entry) {
        entry.movementBroadcastState().beginTick();
    }

    public static void markReconciled(AgentRuntimeEntry entry) {
        entry.movementBroadcastState().markReconciled();
    }

    public static boolean reconciledThisTick(AgentRuntimeEntry entry) {
        return entry.movementBroadcastState().reconciledThisTick();
    }

    public static boolean valid(AgentRuntimeEntry entry) {
        return entry.movementBroadcastState().valid();
    }

    public static int lastVelocityX(AgentRuntimeEntry entry) {
        return entry.movementBroadcastState().velocityX();
    }

    public static int lastVelocityY(AgentRuntimeEntry entry) {
        return entry.movementBroadcastState().velocityY();
    }

    public static boolean matches(AgentRuntimeEntry entry,
                                  int x,
                                  int y,
                                  int velocityX,
                                  int velocityY,
                                  int stance,
                                  int footholdId) {
        return entry.movementBroadcastState().matches(x, y, velocityX, velocityY, stance, footholdId);
    }

    public static void record(AgentRuntimeEntry entry,
                              int x,
                              int y,
                              int velocityX,
                              int velocityY,
                              int stance,
                              int footholdId) {
        entry.movementBroadcastState().record(x, y, velocityX, velocityY, stance, footholdId);
    }
}
