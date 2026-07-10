package server.agents.capabilities.movement;

/**
 * Mutable per-Agent formation offset state.
 */
public final class AgentFormationOffsetState {
    private int followOffsetX = 0;

    public int followOffsetX() {
        return followOffsetX;
    }

    public void setFollowOffsetX(int followOffsetX) {
        this.followOffsetX = followOffsetX;
    }
}
