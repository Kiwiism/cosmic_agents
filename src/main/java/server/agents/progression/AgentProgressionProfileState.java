package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentProgressionProfileState {
    public static final AgentCapabilityStateKey<AgentProgressionProfileState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.personality",
                    AgentProgressionProfileState.class, AgentProgressionProfileState::new);

    private AgentProgressionProfile profile;

    public synchronized AgentProgressionProfile profile() {
        return profile;
    }

    public synchronized void assign(AgentProgressionProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("a progression profile is required");
        }
        this.profile = profile;
    }
}
