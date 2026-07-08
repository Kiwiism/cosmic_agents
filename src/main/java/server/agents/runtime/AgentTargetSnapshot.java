package server.agents.runtime;

import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;

import java.awt.Point;

/**
 * Agent-owned read-only target snapshot used by movement, navigation, and tick
 * orchestration.
 */
public record AgentTargetSnapshot(AgentFormationService.FormationState formation,
                                  Point rawOwnerPos,
                                  Point followAnchorPos,
                                  String followAnchorName,
                                  Point followBasePos,
                                  Point followTargetPos,
                                  Point moveTargetPos,
                                  Point farmAnchorPos,
                                  Point grindTargetPos,
                                  Point primaryTargetPos,
                                  String primaryTargetSource) {
    public Point steeringTargetPos(AgentRuntimeEntry entry) {
        Point navTargetPos = AgentNavigationDebugStateRuntime.navTargetPosition(entry);
        return navTargetPos != null ? navTargetPos : new Point(primaryTargetPos);
    }

    public String steeringTargetSource(AgentRuntimeEntry entry) {
        return AgentNavigationDebugStateRuntime.hasNavTargetPosition(entry) ? "nav-waypoint" : primaryTargetSource;
    }
}
