package server.agents.capabilities.looting;

import server.agents.runtime.AgentRuntimeEntry;

/** Boundary used by activity systems to select a loot cadence without owning loot mechanics. */
public final class AgentLootCollectionContextRuntime {
    private AgentLootCollectionContextRuntime() {
    }

    public static void enterFieldGrind(AgentRuntimeEntry entry, int agentId) {
        if (entry != null) {
            entry.capabilityStates().require(AgentLootCollectionContextState.STATE_KEY)
                    .fieldGrind(agentId);
        }
    }

    public static void leaveFieldGrind(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.capabilityStates().require(AgentLootCollectionContextState.STATE_KEY).standard();
        }
    }

    public static AgentLootCollectionContextState.Snapshot snapshot(AgentRuntimeEntry entry) {
        return entry == null
                ? new AgentLootCollectionContextState.Snapshot(
                AgentLootCollectionContextState.Mode.STANDARD, 0)
                : entry.capabilityStates().require(AgentLootCollectionContextState.STATE_KEY).snapshot();
    }
}
