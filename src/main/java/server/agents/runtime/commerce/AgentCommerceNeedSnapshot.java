package server.agents.runtime.commerce;

/** Read-only evidence used to propose, never directly start, a Commerce visit. */
public record AgentCommerceNeedSnapshot(
        double inventoryUtilization,
        int marketableItemCount,
        boolean supplyDeficit,
        boolean equipmentUpgradeAvailable,
        boolean outstandingEconomicIntent,
        long millisSinceLastVisit,
        boolean sessionCapacityAvailable) {
    public AgentCommerceNeedSnapshot {
        if (!Double.isFinite(inventoryUtilization) || inventoryUtilization < 0d
                || inventoryUtilization > 1d || marketableItemCount < 0
                || millisSinceLastVisit < 0L) {
            throw new IllegalArgumentException("invalid Commerce need snapshot");
        }
    }
}
