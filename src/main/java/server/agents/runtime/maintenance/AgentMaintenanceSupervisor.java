package server.agents.runtime.maintenance;

import client.Character;
import server.agents.capabilities.supplies.AgentSupplyProcurementRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Agent-wide interruption point for maintenance that may suspend and later resume foreground intent. */
public final class AgentMaintenanceSupervisor {
    private static final AgentMaintenanceSupervisor RUNTIME = new AgentMaintenanceSupervisor(List.of(
            AgentSupplyProcurementRuntime::tick));

    private final List<AgentMaintenanceHandler> handlers;

    public AgentMaintenanceSupervisor(List<AgentMaintenanceHandler> handlers) {
        if (handlers == null || handlers.stream().anyMatch(handler -> handler == null)) {
            throw new IllegalArgumentException("Maintenance handlers are required");
        }
        this.handlers = List.copyOf(handlers);
    }

    public static boolean tickRuntime(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return RUNTIME.tick(entry, agent, nowMs);
    }

    public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        for (AgentMaintenanceHandler handler : handlers) {
            if (handler.tick(entry, agent, nowMs)) {
                return true;
            }
        }
        return false;
    }
}
