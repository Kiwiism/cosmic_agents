package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

/** Typed access to one Agent's movement-skill session state. */
public final class AgentMovementSkillStateRuntime {
    private AgentMovementSkillStateRuntime() {
    }

    public static AgentMovementSkillState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentMovementSkillState.STATE_KEY);
    }

    public static boolean castReady(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextCastAtMs();
    }

    public static void recordCast(AgentRuntimeEntry entry, long nowMs) {
        state(entry).setNextCastAtMs(nowMs + AgentMovementSkillConfig.CAST_COOLDOWN_MS);
    }

    public static void clearAirborneCast(AgentRuntimeEntry entry) {
        entry.capabilityStates()
                .find(AgentMovementSkillState.STATE_KEY)
                .ifPresent(AgentMovementSkillState::clearAirborneCast);
    }
}
