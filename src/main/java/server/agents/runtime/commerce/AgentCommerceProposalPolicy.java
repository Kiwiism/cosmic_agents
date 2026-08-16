package server.agents.runtime.commerce;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityProposal;

/** Deterministic proposal policy; admission and activity switching remain World Director work. */
public final class AgentCommerceProposalPolicy {
    private static final long PERIODIC_VISIT_MS = 7L * 24L * 60L * 60L * 1_000L;

    public AgentCommerceProposal evaluate(
            AgentCommerceVisitRequest visit,
            AgentCommerceNeedSnapshot needs) {
        if (visit == null || needs == null) {
            throw new IllegalArgumentException("Commerce proposal inputs are required");
        }
        boolean hasWork = needs.outstandingEconomicIntent()
                || needs.supplyDeficit()
                || needs.equipmentUpgradeAvailable()
                || needs.marketableItemCount() > 0
                || needs.inventoryUtilization() >= .80d
                || needs.millisSinceLastVisit() >= PERIODIC_VISIT_MS;
        boolean eligible = needs.sessionCapacityAvailable() && hasWork;
        int priority = needs.outstandingEconomicIntent() ? 90
                : needs.inventoryUtilization() >= .90d ? 80
                : needs.supplyDeficit() ? 70
                : needs.marketableItemCount() > 0 ? 60 : 30;
        long utility = Math.round(needs.inventoryUtilization() * 1_000d)
                + Math.min(1_000, needs.marketableItemCount() * 50L)
                + (needs.supplyDeficit() ? 500L : 0L)
                + (needs.equipmentUpgradeAvailable() ? 300L : 0L)
                + (needs.outstandingEconomicIntent() ? 1_000L : 0L);
        String evidence = "inventory=" + Math.round(needs.inventoryUtilization() * 100d)
                + "% marketable=" + needs.marketableItemCount()
                + " supplyDeficit=" + needs.supplyDeficit()
                + " upgrade=" + needs.equipmentUpgradeAvailable()
                + " intent=" + needs.outstandingEconomicIntent()
                + " capacity=" + needs.sessionCapacityAvailable();
        return new AgentCommerceProposal(visit, new AgentWorldActivityProposal(
                "commerce:" + visit.requestId(), AgentActivityKind.COMMERCE,
                priority, utility, eligible, evidence));
    }
}
