package server.agents.capabilities.movement;

import java.awt.Point;

public record AgentMovementSnapshot(
        boolean following,
        boolean grinding,
        int followTargetId,
        Point moveTarget,
        boolean moveTargetPrecise,
        Point farmAnchor,
        int farmAnchorMapId,
        int patrolRegionId,
        int patrolMapId,
        Point patrolWanderTarget,
        Point botPosition,
        Point ownerPosition,
        AgentMovementMode mode
) {
    public AgentMovementSnapshot {
        moveTarget = copy(moveTarget);
        farmAnchor = copy(farmAnchor);
        patrolWanderTarget = copy(patrolWanderTarget);
        botPosition = copy(botPosition);
        ownerPosition = copy(ownerPosition);
    }

    @Override
    public Point moveTarget() {
        return copy(moveTarget);
    }

    @Override
    public Point farmAnchor() {
        return copy(farmAnchor);
    }

    @Override
    public Point patrolWanderTarget() {
        return copy(patrolWanderTarget);
    }

    @Override
    public Point botPosition() {
        return copy(botPosition);
    }

    @Override
    public Point ownerPosition() {
        return copy(ownerPosition);
    }

    private static Point copy(Point point) {
        return point == null ? null : new Point(point);
    }
}
