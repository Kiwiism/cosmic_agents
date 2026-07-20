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
        BoundedAgentEventBus bus = entry.capabilityStates().require(STATE_KEY);
        AgentSessionEventWiringRuntime.ensureWired(entry, bus);
        return bus;
    }

    static int drain(AgentRuntimeEntry entry, int budget) {
        if (entry == null) {
            return 0;
        }
        return entry.capabilityStates().find(STATE_KEY)
                .map(bus -> bus.drain(budget))
                .orElse(0);
    }

    public static void close(AgentRuntimeEntry entry) {
        AgentSessionEventWiringRuntime.close(entry);
        entry.capabilityStates().remove(STATE_KEY).ifPresent(BoundedAgentEventBus::close);
    }
}
