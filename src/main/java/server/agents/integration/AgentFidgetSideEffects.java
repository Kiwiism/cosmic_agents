package server.agents.integration;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary bot-side gateway for fidget side effects that still live in the
 * legacy bot package.
 */
public final class AgentFidgetSideEffects {
    private AgentFidgetSideEffects() {
    }

    public static void maybeStartSocialFidget(AgentRuntimeEntry entry) {
        AgentFidgetService.maybeStartSocialFidget(entry);
    }

    public static void maybeStartGreetingFidget(AgentRuntimeEntry entry, int roll) {
        AgentFidgetService.maybeStartGreetingFidget(entry, roll);
    }
}
