package server.agents.capabilities.trade;

/**
 * Mutable state for one queued bot-initiated trade retry.
 */
public final class AgentTradeRetryState {
    private Runnable retry;
    private int delayMs;

    public boolean hasRetry() {
        return retry != null;
    }

    public void queueRetry(Runnable retry, int delayMs) {
        if (hasRetry()) {
            return;
        }
        setRetry(retry);
        setDelayMs(delayMs);
    }

    public void setRetry(Runnable retry) {
        this.retry = retry;
    }

    public Runnable retry() {
        return retry;
    }

    public int delayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = delayMs;
    }

    public Runnable takeRetry() {
        Runnable queuedRetry = retry;
        setRetry(null);
        return queuedRetry;
    }
}
