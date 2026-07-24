package server.agents.runtime.decision;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Stable per-Agent canary routing. The same Agent/domain pair stays on the
 * same implementation across ticks and relogs when the character id is stable.
 */
public final class AgentBehaviorVersionRouter {
    private AgentBehaviorVersionRouter() {
    }

    public static AgentBehaviorRoute route(
            AgentRuntimeEntry entry,
            String domain,
            String legacyVersion,
            String candidateVersion,
            AgentBehaviorRouteMode mode,
            int rolloutPercent,
            long nowMs,
            String correlationId) {
        if (entry == null || domain == null || domain.isBlank()
                || legacyVersion == null || legacyVersion.isBlank()
                || candidateVersion == null || candidateVersion.isBlank()
                || mode == null || rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("Complete behavior route inputs are required");
        }
        int agentId = entry.bot() == null ? 0 : entry.bot().getId();
        boolean canary = Math.floorMod(31 * agentId + domain.hashCode(), 100) < rolloutPercent;
        String execution = switch (mode) {
            case ACTIVE -> candidateVersion;
            case CANARY -> canary ? candidateVersion : legacyVersion;
            case LEGACY, SHADOW -> legacyVersion;
        };
        String shadow = mode == AgentBehaviorRouteMode.SHADOW ? candidateVersion : "";
        AgentBehaviorRoute route = new AgentBehaviorRoute(
                domain, mode, execution, shadow, rolloutPercent);
        entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                nowMs, "behavior-route:" + domain, execution, "version-router",
                execution, mode + " rollout=" + rolloutPercent, correlationId,
                List.of(legacyVersion, candidateVersion));
        return route;
    }
}
