package server.agents.capabilities.combat;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Updates tactical counters from authoritative kill facts. */
public final class AgentCombatTacticalEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentCombatTacticalEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (!(event instanceof AgentMobKilledEvent killed)) {
            return;
        }
        AgentCombatTacticalState.Snapshot previous =
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry);
        if (previous != null
                && previous.lastDecision() == AgentCombatDecisionReason.ROUTE_BLOCKER
                && previous.lastSelectedMobId() == killed.mobId()) {
            entry.capabilityStates().require(AgentRouteBlockerState.STATE_KEY)
                    .killed(killed.occurredAtMs());
        }
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        if (directive == null) {
            return;
        }
        AgentCombatDirectiveRuntime.state(entry).killed(
                killed.mapId(), killed.mobId(),
                directive.requiredMobIds().isEmpty()
                        || directive.requiredMobIds().contains(killed.mobId()),
                killed.occurredAtMs());
    }
}
