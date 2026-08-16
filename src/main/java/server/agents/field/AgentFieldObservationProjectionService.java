package server.agents.field;

import server.agents.events.AgentEvent;
import server.agents.events.AgentEventListener;
import server.agents.field.events.AgentFieldAssignmentChangedEvent;
import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.field.events.AgentFieldPopulationChangedEvent;
import server.agents.field.events.AgentFieldRestEvent;
import server.agents.operations.events.AgentAttackResolvedEvent;
import server.agents.operations.events.AgentCombatPostureChangedEvent;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.runtime.AgentRuntimeEntry;

/** Projects typed field/combat events into one bounded diagnostic read model. */
public final class AgentFieldObservationProjectionService implements AgentEventListener<AgentEvent> {
    private final AgentRuntimeEntry entry;

    public AgentFieldObservationProjectionService(AgentRuntimeEntry entry) {
        this.entry = entry;
    }

    @Override
    public void onAgentEvent(AgentEvent event) {
        AgentFieldObservationState state = entry.capabilityStates()
                .require(AgentFieldObservationState.STATE_KEY);
        if (event instanceof AgentFieldLifecycleEvent lifecycle) {
            state.lifecycle(lifecycle.phase().name(), lifecycle.reason(), lifecycle.occurredAtMs());
        } else if (event instanceof AgentFieldAssignmentChangedEvent assignment) {
            state.assignment(assignment.role(), assignment.reason(), assignment.occurredAtMs());
        } else if (event instanceof AgentFieldPopulationChangedEvent population) {
            state.population(population.change().name(), population.reason(), population.occurredAtMs());
        } else if (event instanceof AgentFieldRestEvent rest) {
            state.rest(rest.phase().name(), rest.reason(), rest.occurredAtMs());
        } else if (event instanceof AgentCombatPostureChangedEvent posture) {
            state.posture(posture.posture(), posture.targetMobId(), posture.targetPosition(),
                    posture.reason(), posture.occurredAtMs());
        } else if (event instanceof AgentAttackResolvedEvent attack) {
            state.attack(attack.hitLines(), attack.missLines());
        } else if (event instanceof AgentMobDamagedEvent damage) {
            state.damage(damage.appliedDamage());
        }
    }
}
