package server.agents.plans;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Global foreground-intent gate. Cross-plan interruptions belong here; concrete plan engines
 * remain replaceable behind this seam.
 */
public final class AgentForegroundPlanRuntime {
    private AgentForegroundPlanRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long wallNowMs) {
        if (AgentTownLifeRuntime.active(entry)) {
            return AgentTownLifeRuntime.tick(entry, agent, wallNowMs);
        }
        return AgentAmherstPlanRuntime.tickGate(entry, agent, wallNowMs);
    }
}
