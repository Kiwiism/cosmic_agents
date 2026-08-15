package server.agents.progression;

import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Keeps hunt recovery on-map while an objective-relevant mob is taking real HP damage. */
public final class AgentHuntRecoveryEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentHuntRecoveryEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentMobDamagedEvent damaged
                && AgentCombatObjectiveTargetStateRuntime.allows(entry, damaged.mobId())) {
            AgentHuntRecoveryRuntime.recordRelevantDamage(
                    entry, damaged.mapId(), damaged.occurredAtMs());
        }
    }
}
