package server.agents.capabilities.navigation;

import java.awt.Point;
import java.util.List;

public record AgentPortalRoutePlan(int internalPortalId, List<Point> waypoints, String description) {
    public static final AgentPortalRoutePlan DIRECT = new AgentPortalRoutePlan(-1, List.of(), "direct route");

    public AgentPortalRoutePlan {
        waypoints = waypoints == null ? List.of() : waypoints.stream().map(Point::new).toList();
        description = description == null || description.isBlank() ? "alternate route" : description;
    }

    public boolean usesInternalPortal() {
        return internalPortalId >= 0;
    }
}
