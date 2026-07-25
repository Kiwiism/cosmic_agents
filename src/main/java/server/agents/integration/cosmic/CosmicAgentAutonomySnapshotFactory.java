package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.runtime.AgentCapabilityView;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.autonomy.AgentAutonomyCycleState;
import server.agents.runtime.autonomy.AgentAutonomySnapshot;

/** Captures mutable Cosmic state once at the autonomy-kernel boundary. */
public final class CosmicAgentAutonomySnapshotFactory {
    private CosmicAgentAutonomySnapshotFactory() {
    }

    public static AgentAutonomySnapshot capture(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            throw new IllegalArgumentException("Agent runtime state is required");
        }
        AgentCapabilityView view =
                CosmicAgentCapabilityViewFactory.create(entry, agent, nowMs);
        long sequence = entry.capabilityStates()
                .require(AgentAutonomyCycleState.STATE_KEY)
                .nextSnapshotSequence();
        return new AgentAutonomySnapshot(
                sequence, nowMs, view.agent(), view.perception());
    }
}
