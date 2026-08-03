package server.agents.capabilities.navigation;

import java.awt.Point;
import java.util.List;

/** Per-agent backing state for observer-only route diagnostics. */
public final class AgentNavigationRouteTraceState {
    private long revision;
    private long plannedAtMs;
    private int mapId = -1;
    private int graphVersion;
    private int speed;
    private int jump;
    private int startRegionId = -1;
    private int targetRegionId = -1;
    private Point targetPosition;
    private String source = "";
    private String reason = "";
    private List<AgentNavigationTraceSnapshot.Edge> path = List.of();
    private int cost = -1;
    private int expandedNodes;
    private long elapsedMicroseconds;
    private boolean reached;
    private boolean bestEffort;
    private boolean capped;
    private int recoveryCount;
    private long lastRecoveryAtMs;
    private String lastRecoveryType = "";

    public synchronized void planned(AgentNavigationGraph graph,
                                     int currentRegionId,
                                     int destinationRegionId,
                                     Point destination,
                                     AgentNavigationPathService.MovementPathSelection selection,
                                     String routeSource,
                                     String routeReason,
                                     long nowMs) {
        revision++;
        plannedAtMs = nowMs;
        mapId = graph.mapId;
        graphVersion = graph.version;
        speed = graph.movementProfile.totalSpeedStat();
        jump = graph.movementProfile.totalJumpStat();
        startRegionId = currentRegionId;
        targetRegionId = destinationRegionId;
        targetPosition = destination == null ? null : new Point(destination);
        source = routeSource == null ? "" : routeSource;
        reason = routeReason == null ? "" : routeReason;
        AgentNavigationPathService.SearchOutcome outcome = selection.outcome();
        path = selection.path().stream()
                .map(AgentNavigationTraceSnapshot.Edge::from)
                .toList();
        cost = outcome.cost() == Integer.MAX_VALUE ? -1 : outcome.cost();
        expandedNodes = outcome.expandedNodes();
        elapsedMicroseconds = outcome.elapsedMicroseconds();
        reached = outcome.reached();
        bestEffort = outcome.bestEffort();
        capped = outcome.capped();
    }

    public synchronized void rejected(AgentNavigationGraph graph,
                                      int currentRegionId,
                                      int destinationRegionId,
                                      Point destination,
                                      String routeSource,
                                      String routeReason,
                                      long nowMs) {
        revision++;
        plannedAtMs = nowMs;
        mapId = graph.mapId;
        graphVersion = graph.version;
        speed = graph.movementProfile.totalSpeedStat();
        jump = graph.movementProfile.totalJumpStat();
        startRegionId = currentRegionId;
        targetRegionId = destinationRegionId;
        targetPosition = destination == null ? null : new Point(destination);
        source = routeSource == null ? "" : routeSource;
        reason = routeReason == null ? "" : routeReason;
        path = List.of();
        cost = -1;
        expandedNodes = 0;
        elapsedMicroseconds = 0L;
        reached = false;
        bestEffort = false;
        capped = false;
    }

    public synchronized void recovered(String recoveryType, long nowMs) {
        recoveryCount++;
        lastRecoveryAtMs = nowMs;
        lastRecoveryType = recoveryType == null ? "" : recoveryType;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(revision, plannedAtMs, mapId, graphVersion, speed, jump,
                startRegionId, targetRegionId,
                targetPosition == null ? null : new Point(targetPosition),
                source, reason, path, cost, expandedNodes, elapsedMicroseconds,
                reached, bestEffort, capped, recoveryCount,
                lastRecoveryAtMs, lastRecoveryType);
    }

    public record Snapshot(long revision,
                           long plannedAtMs,
                           int mapId,
                           int graphVersion,
                           int speed,
                           int jump,
                           int startRegionId,
                           int targetRegionId,
                           Point targetPosition,
                           String source,
                           String reason,
                           List<AgentNavigationTraceSnapshot.Edge> path,
                           int cost,
                           int expandedNodes,
                           long elapsedMicroseconds,
                           boolean reached,
                           boolean bestEffort,
                           boolean capped,
                           int recoveryCount,
                           long lastRecoveryAtMs,
                           String lastRecoveryType) {
    }
}
