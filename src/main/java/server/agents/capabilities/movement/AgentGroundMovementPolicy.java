package server.agents.capabilities.movement;

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
}
