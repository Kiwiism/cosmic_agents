package server.agents.capabilities.runtime;

import server.agents.events.AgentEventBus;
import server.agents.events.BoundedAgentEventBus;
import server.agents.model.AgentSnapshot;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.state.AgentCapabilityStateRegistry;

/** Narrow policy-facing ports supplied to reconstructed capabilities. */
public record AgentCapabilityView(
        AgentSnapshot agent,
        AgentPerceptionSnapshot perception,
        AgentCapabilityStateRegistry states,
        AgentEventBus events,
        AgentCapabilityActionPort actions) {

    public AgentCapabilityView {
        if (agent == null || perception == null || states == null || events == null || actions == null) {
            throw new IllegalArgumentException("Agent snapshot, state, events, and actions are required");
        }
    }

    public static AgentCapabilityView unavailable() {
        return new AgentCapabilityView(
                AgentSnapshot.unavailable(),
                AgentPerceptionSnapshot.unavailable(),
                new AgentCapabilityStateRegistry(),
                new BoundedAgentEventBus(1),
                ignored -> AgentCapabilityActionSubmission.UNSUPPORTED);
    }
}
