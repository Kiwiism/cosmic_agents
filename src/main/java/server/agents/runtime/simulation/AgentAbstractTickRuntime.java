package server.agents.runtime.simulation;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

/** Executes only capability work explicitly proven safe for mutation-free abstraction. */
public final class AgentAbstractTickRuntime {
    private AgentAbstractTickRuntime() {
    }

    public static boolean permits(AgentRuntimeEntry entry, MapleMap map) {
        if (entry == null || map == null || entry.actionMailbox().size() > 0) {
            return false;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || agent.getMap() != map) {
            return false;
        }
        return switch (entry.simulationState().abstractExecutionScope()) {
            case TOWN_LIFE -> AgentTownLifeRuntime.abstractEligible(entry, agent);
            case NONE -> false;
        };
    }

    public static void tick(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null) {
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentAbstractExecutionScope scope = entry.simulationState().abstractExecutionScope();
        if (agent == null || scope == AgentAbstractExecutionScope.NONE) {
            entry.simulationState().backgroundOutcomes()
                    .recordUnsupportedOutcome("abstract tick without an eligible capability");
            return;
        }
        entry.simulationState().backgroundOutcomes().heartbeat(nowMs);
        switch (scope) {
            case TOWN_LIFE -> AgentTownLifeRuntime.tick(entry, agent, nowMs);
            case NONE -> entry.simulationState().backgroundOutcomes()
                    .recordUnsupportedOutcome("unsupported abstract execution scope");
        }
    }

}
