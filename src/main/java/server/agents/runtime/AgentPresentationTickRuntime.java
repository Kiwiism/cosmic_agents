package server.agents.runtime;

import client.Character;
import server.agents.capabilities.presentation.AgentPersonalityPresentationPolicy;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.personality.AgentPersonalityState;

import java.awt.Point;

/** Serialized runtime adapter for presentation independent from active capability type. */
public final class AgentPresentationTickRuntime {
    private AgentPresentationTickRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry,
                               Character agent,
                               Point targetPosition,
                               long nowMs) {
        if (entry == null || agent == null || entry.capabilityStates() == null) {
            return false;
        }
        AgentPersonalityState personality = entry.capabilityStates().require(
                AgentPersonalityState.STATE_KEY);
        if (!personality.presentationEnabled()) {
            return false;
        }
        int realPlayerObservers = CosmicAgentPerceptionSnapshotFactory.capture(agent, nowMs)
                .realPlayerObservers();
        return AgentPersonalityPresentationPolicy.tick(
                entry,
                agent,
                nowMs,
                realPlayerObservers,
                targetPosition,
                AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }
}
