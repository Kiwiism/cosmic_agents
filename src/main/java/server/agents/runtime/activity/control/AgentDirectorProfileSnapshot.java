package server.agents.runtime.activity.control;

import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;

/** Durable personality identity and decision-relevant traits for Director clients. */
public record AgentDirectorProfileSnapshot(
        String profileId,
        int profileVersion,
        Map<String, Integer> traits) {

    public AgentDirectorProfileSnapshot {
        profileId = profileId == null ? "" : profileId.trim();
        traits = Map.copyOf(traits == null ? Map.of() : traits);
    }

    public static AgentDirectorProfileSnapshot capture(AgentRuntimeEntry entry) {
        AgentPersonalityState state = entry.capabilityStates()
                .require(AgentPersonalityState.STATE_KEY);
        AgentPersonalityProfile profile = state.profile();
        if (profile == null) return new AgentDirectorProfileSnapshot("", 0, Map.of());
        AgentPersonalityProfile.Traits traits = profile.traits();
        return new AgentDirectorProfileSnapshot(profile.profileId(), profile.profileVersion(), Map.of(
                "activity", traits.activity(),
                "patience", traits.patience(),
                "expressiveness", traits.expressiveness(),
                "curiosity", traits.curiosity(),
                "sociability", traits.sociability(),
                "riskTolerance", traits.riskTolerance(),
                "routinePreference", traits.routinePreference()));
    }
}
