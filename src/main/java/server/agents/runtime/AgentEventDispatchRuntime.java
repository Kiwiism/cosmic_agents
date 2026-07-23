package server.agents.runtime;

/** Drains session-local facts only at a completed Agent frame boundary. */
public final class AgentEventDispatchRuntime {
    private static final int DEFAULT_MAX_EVENTS_PER_TICK = config.AgentTuning.intValue("server.agents.runtime.AgentEventDispatchRuntime.DEFAULT_MAX_EVENTS_PER_TICK");

    private AgentEventDispatchRuntime() {
    }

    public static int drain(AgentRuntimeEntry entry) {
        int budget = AgentBoundedExecutorFactory.positiveIntegerProperty(
                "agents.events.maxPerTick", DEFAULT_MAX_EVENTS_PER_TICK);
        return AgentSessionEventRuntime.drain(entry, budget);
    }
}
