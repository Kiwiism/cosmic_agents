package server.agents.runtime;

public final class AgentMovementPhysicsCacheState {
    private int lastGroundFootholdId = 0;

    public int lastGroundFootholdId() {
        return lastGroundFootholdId;
    }

    public void setLastGroundFootholdId(int lastGroundFootholdId) {
        this.lastGroundFootholdId = lastGroundFootholdId;
    }
}
