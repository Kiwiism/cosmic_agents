package server.agents.runtime;

public final class AgentTickFailureState {
    private int failureCount = 0;
    private long windowStartedAtMs = 0L;

    public int failureCount() {
        return failureCount;
    }

    public long windowStartedAtMs() {
        return windowStartedAtMs;
    }

    public void resetWindow(long startedAtMs) {
        windowStartedAtMs = startedAtMs;
        failureCount = 0;
    }

    public int incrementFailureCount() {
        failureCount++;
        return failureCount;
    }

    public void clear() {
        failureCount = 0;
        windowStartedAtMs = 0L;
    }
}
