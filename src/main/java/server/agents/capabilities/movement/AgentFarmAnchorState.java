package server.agents.capabilities.movement;

import java.awt.Point;

public final class AgentFarmAnchorState {
    private Point anchor = null;
    private int mapId = -1;

    public Point anchor() {
        return anchor == null ? null : new Point(anchor);
    }

    public int mapId() {
        return mapId;
    }

    public boolean hasAnchor() {
        return anchor != null;
    }

    public void setAnchor(Point anchor, int mapId) {
        this.anchor = anchor == null ? null : new Point(anchor);
        this.mapId = anchor == null ? -1 : mapId;
    }

    public void clear() {
        anchor = null;
        mapId = -1;
    }
}
