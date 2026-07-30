package server.agents.capabilities.looting;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Converts authoritative kill events into a bounded loot-collection batch. */
public final class AgentPostKillLootEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentPostKillLootEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentMobKilledEvent killed) {
            entry.capabilityStates().require(AgentPostKillLootState.STATE_KEY)
                    .recordKill(killed.mobObjectId(), killed.occurredAtMs());
        }
    }
}
