package server.agents.capabilities.navigation;

public final class AgentNavigationDebugState {
    private String lastDecision = "-";
    private String lastEdgeBlockReason = null;
    private boolean graphWarmupFallback = false;

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
