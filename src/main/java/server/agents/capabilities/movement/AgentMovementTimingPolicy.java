package server.agents.capabilities.movement;

public final class AgentMovementTimingPolicy {
    private AgentMovementTimingPolicy() {
    }

    public static int tickDown(int remainingMs, int tickMs) {
        if (remainingMs <= 0) {
            return 0;
        }
        return Math.max(0, remainingMs - Math.max(0, tickMs));
    }

    public static int delayAfterCurrentTick(int durationMs, int tickMs) {
        if (durationMs <= 0) {
            return 0;
        }
        return Math.max(0, durationMs - Math.max(0, tickMs));
    }
}
