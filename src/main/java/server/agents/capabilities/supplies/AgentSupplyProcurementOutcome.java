package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentResourceCategory;

/** Reconciled result of one complete resupply attempt. */
public record AgentSupplyProcurementOutcome(
        Status status,
        AgentResourceCategory category,
        int quantityBefore,
        int quantityAfter,
        int mesosBefore,
        int mesosAfter,
        long occurredAtMs,
        String reason) {

    public enum Status {
        RESTORED,
        PARTIALLY_RESTORED,
        INSUFFICIENT_MESO,
        NO_PROGRESS,
        ROUTE_FAILED,
        SHOP_FAILED
    }

    public AgentSupplyProcurementOutcome {
        if (status == null || category == null || quantityBefore < 0 || quantityAfter < 0
                || mesosBefore < 0 || mesosAfter < 0 || occurredAtMs < 0L) {
            throw new IllegalArgumentException("valid procurement outcome facts are required");
        }
        reason = reason == null ? "" : reason.trim();
    }

    public boolean restored() {
        return status == Status.RESTORED;
    }

    public boolean requiresRecoveryIncome() {
        return status == Status.INSUFFICIENT_MESO
                || status == Status.PARTIALLY_RESTORED
                || status == Status.NO_PROGRESS;
    }
}
