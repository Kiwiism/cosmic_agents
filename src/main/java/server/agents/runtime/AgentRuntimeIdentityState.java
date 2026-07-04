package server.agents.runtime;

import client.Character;

/**
 * Live character identity for one Agent runtime session.
 *
 * <p>The leader reference is mutable because relog/session refresh can replace
 * the live Character object without changing the active Agent session.</p>
 */
public final class AgentRuntimeIdentityState {
    private final Character agent;
    private volatile Character leader;

    public AgentRuntimeIdentityState(Character agent, Character leader) {
        this.agent = agent;
        this.leader = leader;
    }

    public Character agent() {
        return agent;
    }

    public Character leader() {
        return leader;
    }

    public void setLeader(Character leader) {
        this.leader = leader;
    }
}
