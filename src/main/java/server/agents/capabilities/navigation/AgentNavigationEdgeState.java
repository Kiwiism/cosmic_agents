package server.agents.capabilities.navigation;

public final class AgentNavigationEdgeState {
    private AgentNavigationGraph.Edge activeEdge = null;
    private AgentNavigationGraph.Edge jumpLaunchEdge = null;
    private int jumpLaunchX = Integer.MIN_VALUE;

    public boolean hasActiveEdge() {
        return activeEdge != null;
    }

    public AgentNavigationGraph.Edge activeEdge() {
        return activeEdge;
    }

    public void setActiveEdge(Object edge) {
        activeEdge = edge instanceof AgentNavigationGraph.Edge navEdge ? navEdge : null;
    }

    public void clearActiveEdge() {
        activeEdge = null;
    }

    public boolean hasJumpLaunchEdge() {
        return jumpLaunchEdge != null;
    }

    public int jumpLaunchX() {
        return jumpLaunchX;
    }

    public void setJumpLaunch(Object edge, int launchX) {
        jumpLaunchEdge = edge instanceof AgentNavigationGraph.Edge navEdge ? navEdge : null;
        jumpLaunchX = launchX;
    }

    public boolean matchesJumpLaunchEdge(Object edge) {
        return edge instanceof AgentNavigationGraph.Edge navEdge && sameEdge(jumpLaunchEdge, navEdge);
    }

    private static boolean sameEdge(AgentNavigationGraph.Edge a, AgentNavigationGraph.Edge b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.type == b.type
                && a.fromRegionId == b.fromRegionId
                && a.toRegionId == b.toRegionId
                && a.startPoint.equals(b.startPoint)
                && a.endPoint.equals(b.endPoint)
                && a.portalId == b.portalId
                && a.launchMinX == b.launchMinX
                && a.launchMaxX == b.launchMaxX;
    }
}
