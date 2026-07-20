package server.agents.operations.events;

import server.agents.events.AgentEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Current operational blocker projected on the Agent mailbox for supervisor consumption. */
public final class AgentOperationalEvaluationState {
    public static final AgentCapabilityStateKey<AgentOperationalEvaluationState> STATE_KEY =
            new AgentCapabilityStateKey<>("operations.evaluation",
                    AgentOperationalEvaluationState.class,
                    AgentOperationalEvaluationState::new);

    private AgentEvent blocker;

    public synchronized void project(AgentEvent event) {
        if (event instanceof AgentNavigationRouteFailedEvent
                || event instanceof AgentStuckDetectedEvent
                || event instanceof AgentLifeStateChangedEvent life && "DEAD".equals(life.state())) {
            blocker = event;
        } else if (event instanceof AgentRecoveryPerformedEvent
                || event instanceof AgentMapTransitionedEvent
                || event instanceof AgentLifeStateChangedEvent life && "ALIVE".equals(life.state())) {
            blocker = null;
        }
    }

    public synchronized AgentEvent blocker() {
        return blocker;
    }
}
