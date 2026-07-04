package server.agents.runtime;

/**
 * Direction latch used when grind mode has no concrete target yet.
 */
public final class AgentGrindWanderState {
    private int direction;

    public int direction() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = Integer.compare(direction, 0);
    }

    public void clear() {
        direction = 0;
    }
}
