package server.agents.progression;

import server.agents.runtime.AgentRuntimeEntry;

final class AgentProgressionProfileRuntime {
    private AgentProgressionProfileRuntime() {
    }

    static AgentProgressionProfile profile(AgentRuntimeEntry entry) {
        AgentProgressionProfileState state = entry.capabilityStates().require(
                AgentProgressionProfileState.STATE_KEY);
        AgentProgressionProfile profile = state.profile();
        if (profile == null) {
            profile = AgentProgressionProfileRepository.defaultRepository().defaultProfile();
            state.assign(profile);
        }
        return profile;
    }
}
