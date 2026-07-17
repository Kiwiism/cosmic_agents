package server.agents.profiles;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.OptionalLong;

/** Generic profile-facing access to the active plan's objective timing variation. */
public final class AgentObjectiveVariationRuntime {
    private AgentObjectiveVariationRuntime() {
    }

    public static OptionalLong sampleNpcInteractionDelayMs(
            AgentRuntimeEntry entry, AgentBehaviorProfile.DelayRange fallbackRange) {
        return server.agents.capabilities.objective.AgentObjectiveVariationRuntime.sampleNpcInteractionDelayMs(
                entry, fallbackRange);
    }

    public static OptionalLong sampleBetweenObjectivesDelayMs(
            AgentRuntimeEntry entry, AgentBehaviorProfile.DelayRange fallbackRange) {
        return server.agents.capabilities.objective.AgentObjectiveVariationRuntime.sampleBetweenObjectivesDelayMs(
                entry, fallbackRange);
    }
}
