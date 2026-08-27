package server.agents.progression;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Records combat evidence used by bounded Mushroom Kingdom RNG pity. */
public final class AgentMushroomKingdomEventListener implements AgentEventListener<AgentEvent> {
    private static final int HELMET_PEPE_MOB_ID = 3_300_003;
    private final AgentRuntimeEntry entry;

    public AgentMushroomKingdomEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentMobKilledEvent killed) || killed.mobId() != HELMET_PEPE_MOB_ID) return;
        entry.capabilityStates().find(AgentMushroomKingdomState.STATE_KEY)
                .filter(state -> state.phase() == AgentMushroomKingdomState.Phase.ACTIVE
                        && state.currentQuestId() == 2326)
                .ifPresent(AgentMushroomKingdomState::recordHelmetPepeKill);
    }
}
