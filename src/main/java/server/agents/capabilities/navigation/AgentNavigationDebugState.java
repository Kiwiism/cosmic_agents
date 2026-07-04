package server.agents.capabilities.navigation;

import server.agents.monitoring.AgentPathLogger;

public final class AgentNavigationDebugState {
    private AgentPathLogger pathLogger = null;
    private String lastDecision = "-";
    private String lastEdgeBlockReason = null;
    private boolean graphWarmupFallback = false;

    public AgentPathLogger pathLogger() {
        return pathLogger;
    }

    public void setPathLogger(AgentPathLogger pathLogger) {
        this.pathLogger = pathLogger;
    }

    public void clearPathLogger() {
        this.pathLogger = null;
    }

    public String lastDecision() {
        return lastDecision;
    }

    public void setLastDecision(String lastDecision) {
        this.lastDecision = lastDecision;
    }

    public String lastEdgeBlockReason() {
        return lastEdgeBlockReason;
    }

    public void setLastEdgeBlockReason(String lastEdgeBlockReason) {
        this.lastEdgeBlockReason = lastEdgeBlockReason;
    }

    public boolean graphWarmupFallback() {
        return graphWarmupFallback;
    }

    public void setGraphWarmupFallback(boolean graphWarmupFallback) {
        this.graphWarmupFallback = graphWarmupFallback;
    }
}
