package server.agents.capabilities.navigation;

public final class AgentNavigationContinuityState {
    private AgentNavigationGraph graph;
    private int lastRegionId = -1;

    int lastRegionId(AgentNavigationGraph currentGraph) {
        if (graph != currentGraph) {
            graph = currentGraph;
            lastRegionId = -1;
        }
        return lastRegionId;
    }

    void remember(AgentNavigationGraph currentGraph, int regionId) {
        if (graph != currentGraph) {
            graph = currentGraph;
            lastRegionId = -1;
        }
        if (regionId >= 0) {
            lastRegionId = regionId;
        }
    }

    public int lastRegionIdForMap(int mapId) {
        return graph != null && graph.mapId == mapId ? lastRegionId : -1;
    }
}
