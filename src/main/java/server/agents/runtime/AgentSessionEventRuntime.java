package server.agents.runtime;

import server.agents.events.AgentEventBus;
import server.agents.events.BoundedAgentEventBus;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session ownership boundary for the bounded Agent event bus. */
public final class AgentSessionEventRuntime {
    public static final AgentCapabilityStateKey<BoundedAgentEventBus> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.event-bus", BoundedAgentEventBus.class,
                    BoundedAgentEventBus::new);

    private AgentSessionEventRuntime() {
    }

    public static AgentEventBus bus(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(STATE_KEY);
    }

    public static void close(AgentRuntimeEntry entry) {
        entry.capabilityStates().remove(STATE_KEY).ifPresent(BoundedAgentEventBus::close);
    }
}
