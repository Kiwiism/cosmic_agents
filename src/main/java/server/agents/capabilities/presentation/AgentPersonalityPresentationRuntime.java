package server.agents.capabilities.presentation;

import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentPersonalityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/** Runtime assignment and deterministic scheduling; rollout adapters choose activation. */
public final class AgentPersonalityPresentationRuntime {
    private AgentPersonalityPresentationRuntime() {
    }

    public static AgentPersonalityProfile configure(
            AgentRuntimeEntry entry, boolean enabled, long nowMs) {
        AgentPersonalityProfile profile = AgentPersonalityRuntime.restoreOrAssign(
                entry, enabled, nowMs);
        if (entry == null) {
            return null;
        }
        AgentPresentationState state = entry.capabilityStates().require(AgentPresentationState.STATE_KEY);
        state.clear();
        if (profile != null && enabled) {
            schedule(entry, AgentPresentationTrigger.SESSION_STARTED, nowMs);
        }
        return profile;
    }

    public static void schedule(AgentRuntimeEntry entry,
                                AgentPresentationTrigger trigger,
                                long occurredAtMs) {
        if (entry == null || trigger == null) {
            return;
        }
        AgentPersonalityState personality = entry.capabilityStates().require(
                AgentPersonalityState.STATE_KEY);
        if (!personality.presentationEnabled()) {
            return;
        }
        AgentPresentationTelemetry.recordTrigger();
        AgentPresentationState state = entry.capabilityStates().require(AgentPresentationState.STATE_KEY);
        AgentPresentationDecision decision = AgentPersonalityPresentationResolver.resolve(
                personality.profile(), personality.behaviorSeed(), state.nextDecisionSequence(),
                trigger, occurredAtMs);
        if (decision == null) {
            return;
        }
        if (state.schedule(decision)) {
            AgentPresentationTelemetry.recordScheduled();
        } else {
            AgentPresentationTelemetry.recordCoalesced();
        }
    }

    public static double travelHopProbability(AgentRuntimeEntry entry, double fallback) {
        if (entry == null) {
            return fallback;
        }
        AgentPersonalityState state = entry.capabilityStates().require(AgentPersonalityState.STATE_KEY);
        if (!state.presentationEnabled()) {
            return fallback;
        }
        AgentPersonalityProfile.Traits traits = state.profile().traits();
        double probability = 0.01d + traits.activity() / 1_200.0d
                + traits.curiosity() / 2_500.0d + traits.riskTolerance() / 3_000.0d;
        return Math.max(0.01d, Math.min(0.12d, probability));
    }
}
