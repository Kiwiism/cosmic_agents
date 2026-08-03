package server.agents.capabilities.navigation;

import java.util.List;

/** Immutable observer-facing snapshot of one agent's live navigation attempt. */
public record AgentNavigationTraceSnapshot(
        int characterId,
        String characterName,
        int mapId,
        long sampledAtMs,
        int graphVersion,
        int speed,
        int jump,
        long routeRevision,
        long plannedAtMs,
        String routeSource,
        String routeReason,
        String objectiveId,
        Position agentPosition,
        int currentRegionId,
        int targetRegionId,
        Position targetPosition,
        Position waypoint,
        boolean preciseWaypoint,
        String decision,
        String blockReason,
        List<Edge> path,
        int activeEdgeIndex,
        int routeCost,
        int expandedNodes,
        long elapsedMicroseconds,
        boolean reached,
        boolean bestEffort,
        boolean capped,
        int stuckMs,
        int recoveryCooldownMs,
        long lastProgressAtMs,
        String loopKind,
        Edge suppressedEdge,
        long suppressedUntilMs,
        int recoveryCount,
        long lastRecoveryAtMs,
        String lastRecoveryType,
        String verticalStage,
        List<Transition> transitions) {

    public AgentNavigationTraceSnapshot {
        characterName = text(characterName);
        routeSource = text(routeSource);
        routeReason = text(routeReason);
        objectiveId = text(objectiveId);
        decision = text(decision);
        blockReason = text(blockReason);
        loopKind = text(loopKind);
        lastRecoveryType = text(lastRecoveryType);
        verticalStage = text(verticalStage);
        agentPosition = agentPosition == null ? Position.missing() : agentPosition;
        targetPosition = targetPosition == null ? Position.missing() : targetPosition;
        waypoint = waypoint == null ? Position.missing() : waypoint;
        path = path == null ? List.of() : List.copyOf(path);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public record Position(boolean present, int x, int y) {
        public static Position missing() {
            return new Position(false, 0, 0);
        }
    }

    public record Edge(int fromRegionId,
                       int toRegionId,
                       AgentNavigationGraph.EdgeType type,
                       int startX,
                       int startY,
                       int endX,
                       int endY,
                       int launchMinX,
                       int launchMaxX,
                       int launchStepX,
                       int portalId,
                       int ropeX,
                       int ropeTopY,
                       int ropeBottomY,
                       int cost) {
        public static Edge from(AgentNavigationGraph.Edge edge) {
            if (edge == null) {
                return null;
            }
            return new Edge(edge.fromRegionId, edge.toRegionId, edge.type,
                    edge.startPoint.x, edge.startPoint.y,
                    edge.endPoint.x, edge.endPoint.y,
                    edge.launchMinX, edge.launchMaxX, edge.launchStepX,
                    edge.portalId, edge.ropeX, edge.ropeTopY,
                    edge.ropeBottomY, edge.cost);
        }

        public boolean matches(AgentNavigationGraph.Edge edge) {
            return edge != null
                    && fromRegionId == edge.fromRegionId
                    && toRegionId == edge.toRegionId
                    && type == edge.type
                    && startX == edge.startPoint.x
                    && startY == edge.startPoint.y
                    && endX == edge.endPoint.x
                    && endY == edge.endPoint.y;
        }
    }

    public record Transition(int fromRegionId, int toRegionId, long timestampMs) {
    }
}
