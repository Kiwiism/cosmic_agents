package server.agents.behavior;

import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

/** Behavior boundary used by combat, navigation, presentation, and scheduling adapters. */
public final class AgentBehaviorRuntime {
    private AgentBehaviorRuntime() {
    }

    public static void configure(AgentRuntimeEntry entry, boolean enabled) {
        if (entry == null) return;
        AgentPersonalityState personality = entry.capabilityStates().require(AgentPersonalityState.STATE_KEY);
        if (personality.profile() == null || personality.assignment() == null) return;
        AgentBehaviorPolicyProfile policy = AgentBehaviorPolicyRepository.defaultRepository()
                .resolve(personality.profile().profileId());
        entry.capabilityStates().require(AgentBehaviorCalibrationState.STATE_KEY)
                .configure(policy, personality.behaviorSeed(), enabled);
    }

    public static boolean enabled(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentBehaviorCalibrationState.STATE_KEY)
                .map(AgentBehaviorCalibrationState::enabled).orElse(false);
    }

    public static AgentBehaviorPolicyProfile policy(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates().find(AgentBehaviorCalibrationState.STATE_KEY)
                .map(AgentBehaviorCalibrationState::policy).orElse(null);
    }

    public static AgentBehaviorCalibrationState calibration(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBehaviorCalibrationState.STATE_KEY);
    }

    public static AgentBehaviorAdaptationState adaptation(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentBehaviorAdaptationState.STATE_KEY);
    }
}
