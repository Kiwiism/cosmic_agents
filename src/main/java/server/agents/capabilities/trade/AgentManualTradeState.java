package server.agents.capabilities.trade;

public final class AgentManualTradeState {
    private int acceptDelayMs = 0;
    private Object tradeRef = null;
    private int timeoutMs = 0;

    public int acceptDelayMs() {
        return acceptDelayMs;
    }

    public void setAcceptDelayMs(int acceptDelayMs) {
        this.acceptDelayMs = acceptDelayMs;
    }

    public Object tradeRef() {
        return tradeRef;
    }

    public void setTradeRef(Object tradeRef) {
        this.tradeRef = tradeRef;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void begin(Object trade, int timeoutMs) {
        this.acceptDelayMs = 0;
        this.tradeRef = trade;
        this.timeoutMs = timeoutMs;
    }

    public void ensureAcceptDelay(int delayMs) {
        if (acceptDelayMs == 0) {
            acceptDelayMs = delayMs;
        }
    }

    public void clear() {
        acceptDelayMs = 0;
        tradeRef = null;
        timeoutMs = 0;
    }
}
