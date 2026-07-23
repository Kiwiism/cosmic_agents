package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

/** Optional town-specific arrival ceremony before generic activities begin. */
interface AgentTownLifeArrivalExtension {
    boolean tickTravel(AgentRuntimeEntry entry,
                       Character agent,
                       AgentTownLifeState state,
                       long nowMs,
                       PrimitiveCapabilityGateway gateway);

    boolean tickArrival(AgentRuntimeEntry entry,
                        Character agent,
                        AgentTownLifeState state,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway);
}
