package server.agents.runtime.maintenance;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** One bounded, higher-priority maintenance workflow. */
@FunctionalInterface
public interface AgentMaintenanceHandler {
    boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs);
}
