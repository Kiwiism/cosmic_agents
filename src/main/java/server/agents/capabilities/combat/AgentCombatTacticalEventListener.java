package server.agents.capabilities.combat;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Updates tactical state from authoritative damage and kill facts. */
public final class AgentCombatTacticalEventListener implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentCombatTacticalEventListener(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        if (event instanceof AgentMobDamagedEvent damaged) {
            recordBlockerDamage(damaged);
            return;
        }
        if (!(event instanceof AgentMobKilledEvent killed)) {
            return;
        }
        AgentCombatLocalTargetLeaseRuntime.recordKill(
                entry, killed.mapId(), killed.objectiveId(),
                AgentCombatObjectiveTargetStateRuntime.prefers(entry, killed.mobId()),
                killed.occurredAtMs());
        entry.capabilityStates().find(AgentCombatDecisionState.STATE_KEY)
                .ifPresent(state -> state.platformBatch().killed(
                        killed.mapId(), killed.objectiveId(), killed.occurredAtMs()));
        AgentCombatTacticalState.Snapshot previous =
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry);
        if (previous != null
                && previous.lastDecision() == AgentCombatDecisionReason.ROUTE_BLOCKER
                && previous.lastSelectedMobId() == killed.mobId()) {
            AgentCombatDecisionStateRuntime.state(entry).routeBlocker()
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

    private void recordBlockerDamage(AgentMobDamagedEvent damaged) {
        AgentGrindTargetCommitmentService.recordDamageProgress(
                entry, damaged.mobObjectId(), damaged.occurredAtMs());
        AgentCombatTacticalState.Snapshot previous =
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry);
        if (previous != null
                && previous.lastDecision() == AgentCombatDecisionReason.ROUTE_BLOCKER
                && previous.lastSelectedMobId() == damaged.mobId()) {
            AgentCombatDecisionStateRuntime.state(entry).routeBlocker()
                    .damaged(damaged.occurredAtMs());
        }
    }
}
