package server.agents.runtime;

public final class AgentTickSliceState {
    private boolean enabled;
    private int maxSlicesPerTurn = 1;
    private int maxContinuationsPerFrame = 1;
    private AgentTickFrame frame;
    private int continuationCount;
    private long accumulatedExecutionNs;
    private volatile boolean continuationPending;

    public void configure(boolean enabled, int maxSlicesPerTurn, int maxContinuationsPerFrame) {
        if (maxSlicesPerTurn < 1 || maxContinuationsPerFrame < 1) {
            throw new IllegalArgumentException("Agent tick slice limits must be positive");
        }
        this.enabled = enabled;
        this.maxSlicesPerTurn = maxSlicesPerTurn;
        this.maxContinuationsPerFrame = maxContinuationsPerFrame;
        if (!enabled) {
            clear();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxSlicesPerTurn() {
        return maxSlicesPerTurn;
    }

    public boolean continuationPending() {
        return continuationPending;
    }

    AgentTickFrame frame() {
        return frame;
    }

    void startFrame(AgentTickFrame frame) {
        if (frame == null || this.frame != null) {
            throw new IllegalStateException("Agent tick frame cannot be started");
        }
        this.frame = frame;
        continuationCount = 0;
        accumulatedExecutionNs = 0L;
        continuationPending = false;
    }

    void beginTurn() {
        continuationPending = false;
    }

    void recordExecution(long elapsedNs) {
        accumulatedExecutionNs += Math.max(0L, elapsedNs);
    }

    long accumulatedExecutionNs() {
        return accumulatedExecutionNs;
    }

    void requestContinuation() {
        continuationCount++;
        if (continuationCount > maxContinuationsPerFrame) {
            throw new IllegalStateException("Agent tick frame exceeded continuation limit");
        }
        continuationPending = true;
    }

    public void clear() {
        frame = null;
        continuationCount = 0;
        accumulatedExecutionNs = 0L;
        continuationPending = false;
    }
}
