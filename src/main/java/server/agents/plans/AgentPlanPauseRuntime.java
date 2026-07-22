package server.agents.plans;

import server.agents.runtime.AgentRuntimeEntry;

public final class AgentPlanPauseRuntime {
    private AgentPlanPauseRuntime() {
    }

    public static void pause(AgentRuntimeEntry entry, String reason, long nowMs) {
        if (entry != null) entry.capabilityStates().require(AgentPlanPauseState.STATE_KEY).pause(reason, nowMs);
    }

    public static void resume(AgentRuntimeEntry entry, String reason, long nowMs) {
        if (entry != null) entry.capabilityStates().require(AgentPlanPauseState.STATE_KEY).resume(reason, nowMs);
    }

    public static boolean paused(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentPlanPauseState.STATE_KEY)
                .map(AgentPlanPauseState::paused).orElse(false);
    }

    public static long effectiveNow(AgentRuntimeEntry entry, long wallNowMs) {
        return entry == null ? wallNowMs : entry.capabilityStates().require(AgentPlanPauseState.STATE_KEY)
                .effectiveNow(wallNowMs);
    }

    public static void reset(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.capabilityStates().remove(AgentPlanPauseState.STATE_KEY);
        }
    }
}
