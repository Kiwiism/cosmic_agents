package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Capability-owned adapter for no-target grind wandering state.
 */
public final class AgentGrindWanderStateRuntime {
    private AgentGrindWanderStateRuntime() {
    }

    public static int wanderDirection(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentGrindWanderState.STATE_KEY).direction();
    }

    public static void setWanderDirection(AgentRuntimeEntry entry, int direction) {
        entry.capabilityStates().require(AgentGrindWanderState.STATE_KEY).setDirection(direction);
    }

    public static void clearWanderDirection(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentGrindWanderState.STATE_KEY).clear();
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
