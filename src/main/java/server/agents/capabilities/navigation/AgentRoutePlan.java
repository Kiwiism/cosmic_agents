package server.agents.capabilities.navigation;

import server.agents.model.AgentPosition;

import java.util.List;

/** Immutable navigation-policy output consumed by movement mechanics. */
public record AgentRoutePlan(
        String requestId,
        int mapId,
        List<AgentPosition> waypoints,
        String graphVersion,
        long plannedAtMs) {

    public AgentRoutePlan {
        if (requestId == null || requestId.isBlank() || mapId < 0 || waypoints == null
                || graphVersion == null || graphVersion.isBlank() || plannedAtMs < 0) {
            throw new IllegalArgumentException("Valid route identity, waypoints, and graph version are required");
        }
        waypoints = List.copyOf(waypoints);
    }
}
