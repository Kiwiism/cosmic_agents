package server.agents.progression;

import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Keeps hunt recovery on-map while an objective-relevant mob is taking damage or being killed. */
public final class AgentHuntRecoveryEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentHuntRecoveryEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentMobDamagedEvent damaged
                && AgentCombatObjectiveTargetStateRuntime.prefers(entry, damaged.mobId())) {
            AgentHuntRecoveryRuntime.recordRelevantDamage(
                    entry, damaged.mapId(), damaged.occurredAtMs());
        } else if (event instanceof AgentMobKilledEvent killed
                && AgentCombatObjectiveTargetStateRuntime.prefers(entry, killed.mobId())) {
            AgentHuntRecoveryRuntime.recordRelevantKill(
                    entry, killed.mapId(), killed.occurredAtMs());
        }
    }
}
