package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed no-target grind wandering state.
 */
public final class AgentGrindWanderStateRuntime {
    private AgentGrindWanderStateRuntime() {
    }

    public static int wanderDirection(AgentRuntimeEntry entry) {
        return entry.grindWanderState().direction();
    }

    public static void setWanderDirection(AgentRuntimeEntry entry, int direction) {
        entry.grindWanderState().setDirection(direction);
    }

    public static void clearWanderDirection(AgentRuntimeEntry entry) {
        entry.grindWanderState().clear();
    }

    public static int ensureWanderDirection(AgentRuntimeEntry entry) {
        int direction = wanderDirection(entry);
        if (direction == 0) {
            direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            setWanderDirection(entry, direction);
        }
        return direction;
    }
}
