package server.agents.capabilities.movement;

import java.awt.Point;

public final class AgentPatrolState {
    private int regionId = -1;
    private int mapId = -1;
    private Point wanderTarget = null;

    public boolean hasRegion() {
        return regionId >= 0;
    }

    public int regionId() {
        return regionId;
    }

    public int mapId() {
        return mapId;
    }

    public Point wanderTarget() {
        return wanderTarget == null ? null : new Point(wanderTarget);
    }

    public void setRegion(int regionId, int mapId) {
        this.regionId = regionId;
        this.mapId = regionId < 0 ? -1 : mapId;
        wanderTarget = null;
    }

    public void setWanderTarget(Point target) {
        wanderTarget = target == null ? null : new Point(target);
    }

    public void clearWanderTarget() {
        wanderTarget = null;
    }

    public void clear() {
        regionId = -1;
        mapId = -1;
        wanderTarget = null;
    }
}
