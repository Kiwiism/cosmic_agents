package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/**
 * Direction latch used when grind mode has no concrete target yet.
 */
public final class AgentGrindWanderState {
    public static final AgentCapabilityStateKey<AgentGrindWanderState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.grind-wander",
                    AgentGrindWanderState.class, AgentGrindWanderState::new);

    private int direction;

    public int direction() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = Integer.compare(direction, 0);
    }

    public void clear() {
        direction = 0;
    }
}
