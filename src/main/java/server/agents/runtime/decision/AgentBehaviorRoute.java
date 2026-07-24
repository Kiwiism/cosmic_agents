package server.agents.runtime.decision;

/** Deterministic version route selected for one Agent and decision domain. */
public record AgentBehaviorRoute(
        String domain,
        AgentBehaviorRouteMode mode,
        String executionVersion,
        String shadowVersion,
        int rolloutPercent) {

    public AgentBehaviorRoute {
        if (domain == null || domain.isBlank() || mode == null
                || executionVersion == null || executionVersion.isBlank()
                || shadowVersion == null || rolloutPercent < 0 || rolloutPercent > 100) {
            throw new IllegalArgumentException("Valid behavior route is required");
        }
    }

    public boolean shadowEnabled() {
        return !shadowVersion.isBlank();
    }
}
