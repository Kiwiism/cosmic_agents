package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.AgentForegroundActivityDefaults;

/**
 * Global foreground-intent gate. Cross-plan interruptions belong here; concrete plan engines
 * remain replaceable behind this seam.
 */
public final class AgentForegroundPlanRuntime {
    private AgentForegroundPlanRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long wallNowMs) {
        return AgentForegroundActivityDefaults.arbiter().tick(entry, agent, wallNowMs);
    }
}
