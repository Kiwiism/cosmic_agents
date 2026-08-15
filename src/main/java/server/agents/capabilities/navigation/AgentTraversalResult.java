package server.agents.capabilities.navigation;

import java.awt.Point;

/** Typed outcome of attempting one traversal command. */
public record AgentTraversalResult(
        Status status,
        Point targetPosition,
        boolean consumedTick,
        String reason) {

    public enum Status {
        EXECUTED,
        DEFERRED,
        REJECTED
    }

    public AgentTraversalResult {
        targetPosition = targetPosition == null ? null : new Point(targetPosition);
        reason = reason == null ? "" : reason;
    }

    @Override
    public Point targetPosition() {
        return targetPosition == null ? null : new Point(targetPosition);
    }

    public static AgentTraversalResult executed(Point targetPosition, boolean consumedTick) {
        return new AgentTraversalResult(Status.EXECUTED, targetPosition, consumedTick, "");
    }

    public static AgentTraversalResult deferred(String reason) {
        return new AgentTraversalResult(Status.DEFERRED, null, false, reason);
    }

    public static AgentTraversalResult rejected(String reason) {
        return new AgentTraversalResult(Status.REJECTED, null, false, reason);
    }

    public boolean executed() {
        return status == Status.EXECUTED;
    }

    public boolean rejected() {
        return status == Status.REJECTED;
    }
}
