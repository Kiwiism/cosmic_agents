package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed skill-buff debug state.
 */
public final class AgentSkillBuffDebugStateRuntime {
    private AgentSkillBuffDebugStateRuntime() {
    }

    public static long lastActionAtMs(AgentRuntimeEntry entry) {
        return entry.buffState().lastSkillActionAtMs();
    }

    public static String lastActionSummary(AgentRuntimeEntry entry) {
        return entry.buffState().lastSkillActionSummary();
    }

    public static long lastActionAgeMs(AgentRuntimeEntry entry, long nowMs) {
        return entry.buffState().lastSkillActionAgeMs(nowMs);
    }

    public static void rememberAction(AgentRuntimeEntry entry, long atMs, String summary) {
        entry.buffState().rememberSkillAction(atMs, summary);
    }
}
