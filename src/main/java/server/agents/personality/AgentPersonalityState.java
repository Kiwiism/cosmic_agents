package server.agents.personality;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session projection of a durable personality assignment. */
public final class AgentPersonalityState {
    public static final AgentCapabilityStateKey<AgentPersonalityState> STATE_KEY =
            new AgentCapabilityStateKey<>("personality.identity",
                    AgentPersonalityState.class, AgentPersonalityState::new);

    private AgentPersonalityAssignment assignment;
    private AgentPersonalityProfile profile;
    private boolean presentationEnabled;

    public synchronized void assign(AgentPersonalityAssignment assignment,
                                    AgentPersonalityProfile profile,
                                    boolean presentationEnabled) {
        if (assignment == null || profile == null
                || !assignment.personalityProfileId().equals(profile.profileId())
                || assignment.personalityProfileVersion() != profile.profileVersion()) {
            throw new IllegalArgumentException("matching personality assignment and profile are required");
        }
        this.assignment = assignment;
        this.profile = profile;
        this.presentationEnabled = presentationEnabled;
    }

    public synchronized AgentPersonalityAssignment assignment() {
        return assignment;
    }

    public synchronized AgentPersonalityProfile profile() {
        return profile;
    }

    public synchronized long behaviorSeed() {
        return assignment == null ? 0L : assignment.behaviorSeed();
    }

    public synchronized boolean presentationEnabled() {
        return presentationEnabled && assignment != null && profile != null;
    }

    public synchronized void setPresentationEnabled(boolean enabled) {
        presentationEnabled = enabled && assignment != null && profile != null;
    }
}
