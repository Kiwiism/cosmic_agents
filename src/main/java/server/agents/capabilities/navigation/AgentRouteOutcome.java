package server.agents.capabilities.navigation;

/** Immutable routing observation returned through the primitive capability boundary. */
public record AgentRouteOutcome(
        AgentRouteStatus status,
        int sourceMapId,
        int nextMapId,
        int destinationMapId,
        boolean edgeBlocked) {

    public AgentRouteOutcome {
        if (status == null) {
            throw new IllegalArgumentException("route status is required");
        }
    }

    public static AgentRouteOutcome unavailable(int sourceMapId, int destinationMapId) {
        return new AgentRouteOutcome(
                AgentRouteStatus.NO_ROUTE, sourceMapId, -1, destinationMapId, false);
    }
}
