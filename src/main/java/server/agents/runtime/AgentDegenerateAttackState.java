package server.agents.runtime;

/**
 * Latch set after the one allowed close-range hit for ranged-grind spacing.
 */
public final class AgentDegenerateAttackState {
    private boolean done;

    public boolean done() {
        return done;
    }

    public void markDone() {
        done = true;
    }

    public void clear() {
        done = false;
    }
}
