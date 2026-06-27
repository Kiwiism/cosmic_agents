package server.agents.capabilities.movement;

import java.awt.Point;

public record AgentMovementTargetSnapshot(
        String formationType,
        int formationPx,
        int formationSnapRange,
        Point rawOwnerPosition,
        Point followAnchorPosition,
        String followAnchorName,
        Point followBasePosition,
        Point followTargetPosition,
        Point moveTargetPosition,
        Point farmAnchorPosition,
        Point grindTargetPosition,
        Point primaryTargetPosition,
        String primaryTargetSource,
        Point steeringTargetPosition,
        String steeringTargetSource
) {
    public AgentMovementTargetSnapshot {
        rawOwnerPosition = copy(rawOwnerPosition);
        followAnchorPosition = copy(followAnchorPosition);
        followBasePosition = copy(followBasePosition);
        followTargetPosition = copy(followTargetPosition);
        moveTargetPosition = copy(moveTargetPosition);
        farmAnchorPosition = copy(farmAnchorPosition);
        grindTargetPosition = copy(grindTargetPosition);
        primaryTargetPosition = copy(primaryTargetPosition);
        steeringTargetPosition = copy(steeringTargetPosition);
    }

    @Override
    public Point rawOwnerPosition() {
        return copy(rawOwnerPosition);
    }

    @Override
    public Point followAnchorPosition() {
        return copy(followAnchorPosition);
    }

    @Override
    public Point followBasePosition() {
        return copy(followBasePosition);
    }

    @Override
    public Point followTargetPosition() {
        return copy(followTargetPosition);
    }

    @Override
    public Point moveTargetPosition() {
        return copy(moveTargetPosition);
    }

    @Override
    public Point farmAnchorPosition() {
        return copy(farmAnchorPosition);
    }

    @Override
    public Point grindTargetPosition() {
        return copy(grindTargetPosition);
    }

    @Override
    public Point primaryTargetPosition() {
        return copy(primaryTargetPosition);
    }

    @Override
    public Point steeringTargetPosition() {
        return copy(steeringTargetPosition);
    }

    private static Point copy(Point point) {
        return point == null ? null : new Point(point);
    }
}
