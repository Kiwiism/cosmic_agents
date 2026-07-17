package server.agents.capabilities.build.profiles;

public final class AgentSpBuildProfileState {
    private AgentSpBuildProfile profile;

    public AgentSpBuildProfile profile() {
        return profile;
    }

    public void assign(AgentSpBuildProfile profile) {
        this.profile = profile;
    }

    public boolean hasProfile() {
        return profile != null;
    }
}
