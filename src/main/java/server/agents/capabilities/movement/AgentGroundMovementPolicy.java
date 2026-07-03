package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentNavigationGraph;

public final class AgentGroundMovementPolicy {
    private AgentGroundMovementPolicy() {
    }

    public static int calcStepX(int botX,
                                int targetX,
                                boolean wasMovingX,
                                int stopDist,
                                int followDist,
                                int walkStep) {
        int dx = targetX - botX;
        int absDx = Math.abs(dx);
        if (absDx <= stopDist) {
            return 0;
        }
        if (!wasMovingX && absDx <= followDist) {
            return 0;
        }
        return Math.min(absDx, walkStep) * (dx >= 0 ? 1 : -1);
    }

    /**
     * Stop-distance used when navPreciseTarget is true.
     * WALK edges use 4px to absorb terrain micro-bumps on sloped footholds.
     * JUMP and straight down-jump DROP edges use 0px because the agent must walk into the
     * authored launch window, not stop just outside it. Other precise edge types
     * (CLIMB, PORTAL, non-windowed fallback cases) use 1px to reach the exact anchor.
     */
    public static int preciseNavStopDist(AgentNavigationGraph.Edge navEdge) {
        if (navEdge != null
                && (navEdge.type == AgentNavigationGraph.EdgeType.JUMP
                || (navEdge.type == AgentNavigationGraph.EdgeType.DROP && navEdge.launchStepX == 0))) {
            return 0;
        }
        if (navEdge != null && navEdge.type != AgentNavigationGraph.EdgeType.WALK) {
            return 1;
        }
        return 4;
    }

    public static boolean isDirectionalDropEdge(AgentNavigationGraph.Edge navEdge) {
        return navEdge != null
                && navEdge.type == AgentNavigationGraph.EdgeType.DROP
                && navEdge.launchStepX != 0;
    }
}
