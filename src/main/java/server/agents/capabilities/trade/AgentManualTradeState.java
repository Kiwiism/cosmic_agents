package server.agents.capabilities.trade;

import server.Trade;

public final class AgentManualTradeState {
    private int acceptDelayMs = 0;
    private Trade tradeRef = null;
    private int timeoutMs = 0;

    public int acceptDelayMs() {
        return acceptDelayMs;
    }

    public void setAcceptDelayMs(int acceptDelayMs) {
        this.acceptDelayMs = acceptDelayMs;
    }

    public Trade tradeRef() {
        return tradeRef;
    }

    public void setTradeRef(Trade tradeRef) {
        this.tradeRef = tradeRef;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void begin(Trade trade, int timeoutMs) {
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
