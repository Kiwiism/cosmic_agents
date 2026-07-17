package server.agents.capabilities.build.profiles;

public final class AgentApBuildProfileState {
    private AgentApBuildProfile profile;

    public AgentApBuildProfile profile() {
        return profile;
    }

    public void assign(AgentApBuildProfile profile) {
        this.profile = profile;
    }

    public boolean hasProfile() {
        return profile != null;
    }
}
