package server.agents.runtime;

import client.Character;

/**
 * Live character identity for one Agent runtime session.
 *
 * <p>Relationships such as follow targets, cohorts, and command interaction
 * targets deliberately live outside identity.</p>
 */
public final class AgentRuntimeIdentityState {
    private final Character agent;

    public AgentRuntimeIdentityState(Character agent) {
        this.agent = agent;
    }

    public Character agent() {
        return agent;
    }

}
