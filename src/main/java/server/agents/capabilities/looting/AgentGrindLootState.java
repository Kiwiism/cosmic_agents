package server.agents.capabilities.looting;

import server.maps.MapItem;

public final class AgentGrindLootState {
    private MapItem target = null;
    private int ignoredObjectId = 0;
    private long ignoredUntilMs = 0L;

    public MapItem target() {
        return target;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public void setTarget(MapItem target) {
        this.target = target;
    }

    public void clearTarget() {
        target = null;
    }

    public int ignoredObjectId() {
        return ignoredObjectId;
    }

    public long ignoredUntilMs() {
        return ignoredUntilMs;
    }

    public void suppressRetry(int objectId, long untilMs) {
        ignoredObjectId = objectId;
        ignoredUntilMs = untilMs;
    }

    public void clearRetrySuppression() {
        ignoredObjectId = 0;
        ignoredUntilMs = 0L;
    }
}
