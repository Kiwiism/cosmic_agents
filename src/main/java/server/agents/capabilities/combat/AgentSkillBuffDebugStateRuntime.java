package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Capability-owned adapter for skill-buff debug state.
 */
public final class AgentSkillBuffDebugStateRuntime {
    private AgentSkillBuffDebugStateRuntime() {
    }

    public static long lastActionAtMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).lastSkillActionAtMs();
    }

    public static String lastActionSummary(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).lastSkillActionSummary();
    }

    public static long lastActionAgeMs(AgentRuntimeEntry entry, long nowMs) {
        return entry.capabilityStates().require(AgentBuffState.STATE_KEY).lastSkillActionAgeMs(nowMs);
    }

    public static void rememberAction(AgentRuntimeEntry entry, long atMs, String summary) {
        entry.capabilityStates().require(AgentBuffState.STATE_KEY).rememberSkillAction(atMs, summary);
    }
}
