package server.agents.capabilities.navigation;

import java.awt.Point;

public final class AgentNavigationTargetState {
    private Point position = null;
    private int regionId = -1;
    private boolean precise = false;

    public Point position() {
        return position == null ? null : new Point(position);
    }

    public void setPosition(Point position) {
        this.position = position == null ? null : new Point(position);
    }

    public boolean hasPosition() {
        return position != null;
    }

    public int regionId() {
        return regionId;
    }

    public void setRegionId(int regionId) {
        this.regionId = regionId;
    }

    public boolean precise() {
        return precise;
    }

    public void setPrecise(boolean precise) {
        this.precise = precise;
    }

    public void clear() {
        position = null;
        regionId = -1;
        precise = false;
    }
}
