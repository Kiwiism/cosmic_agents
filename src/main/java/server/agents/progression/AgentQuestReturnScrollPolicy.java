package server.agents.progression;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Comparator;

/** Optional travel optimization for quest hunts that finish several maps away from their turn-in NPC. */
final class AgentQuestReturnScrollPolicy {
    private static final int MIN_ROUTE_EDGES = config.AgentTuning.intValue(
            "server.agents.progression.AgentQuestReturnScrollPolicy.MIN_ROUTE_EDGES");

    enum Preparation {
        READY,
        WAITING
    }

    private AgentQuestReturnScrollPolicy() {
    }

    static Preparation prepare(AgentRuntimeEntry entry,
                               Character agent,
                               String tripKey,
                               int huntMapId,
                               int completionMapId,
                               long nowMs,
                               PrimitiveCapabilityGateway gateway) {
        AgentVictoriaLevel15Catalog.ReturnScroll scroll = selectScroll(huntMapId, completionMapId);
        if (scroll == null) {
            clear(entry);
            return Preparation.READY;
        }
        AgentQuestReturnScrollState state = entry.capabilityStates().require(
                AgentQuestReturnScrollState.STATE_KEY);
        state.begin(tripKey);
        state.markReturnEligible(scroll.itemId(), completionMapId);
        if (gateway.itemCount(agent, scroll.itemId()) > 0) {
            return Preparation.READY;
        }
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            return Preparation.WAITING;
        }
        if (state.purchaseAttempted()) {
            return Preparation.READY;
        }
        if (agent.getMeso() < scroll.unitPrice()) {
            state.markPurchaseAttempted();
            return Preparation.READY;
        }
        AgentVictoriaRouteRuntime.TravelOutcome travel = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, scroll.shopMapId(), gateway, nowMs);
        if (travel.status() == AgentVictoriaRouteRuntime.Status.MOVING
                || travel.status() == AgentVictoriaRouteRuntime.Status.PORTAL_UNAVAILABLE) {
            return Preparation.WAITING;
        }
        if (travel.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            state.markPurchaseAttempted();
            return Preparation.READY;
        }
        state.markPurchaseAttempted();
        return AgentShopService.requestVisitAtNpc(entry, agent, scroll.shopNpcId(), 0,
                scroll.itemId(), 1)
                ? Preparation.WAITING : Preparation.READY;
    }

    static boolean useForReturn(AgentRuntimeEntry entry,
                                Character agent,
                                int completionMapId,
                                PrimitiveCapabilityGateway gateway) {
        AgentQuestReturnScrollState state = entry.capabilityStates().require(
                AgentQuestReturnScrollState.STATE_KEY);
        boolean used = state.returnEligible(completionMapId)
                && agent.getMapId() != completionMapId
                && state.returnScrollItemId() > 0
                && gateway.itemCount(agent, state.returnScrollItemId()) > 0
                && gateway.useItem(agent, state.returnScrollItemId());
        if (used) {
            state.clear();
        }
        return used;
    }

    static boolean qualifies(int huntMapId, int completionMapId) {
        return selectScroll(huntMapId, completionMapId) != null;
    }

    static void clear(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentQuestReturnScrollState.STATE_KEY).clear();
    }

    static AgentVictoriaLevel15Catalog.ReturnScroll selectScroll(int huntMapId, int completionMapId) {
        int directDistance = AgentVictoriaTrainingRouteCatalog.distance(huntMapId, completionMapId);
        if (directDistance < MIN_ROUTE_EDGES) {
            return null;
        }
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().returnScrolls().stream()
                .filter(scroll -> {
                    int remainingDistance = AgentVictoriaTrainingRouteCatalog.distance(
                            scroll.townMapId(), completionMapId);
                    return remainingDistance >= 0 && remainingDistance < directDistance;
                })
                .min(Comparator
                        .comparingInt((AgentVictoriaLevel15Catalog.ReturnScroll scroll) ->
                                AgentVictoriaTrainingRouteCatalog.distance(
                                        scroll.townMapId(), completionMapId))
                        .thenComparingInt(AgentVictoriaLevel15Catalog.ReturnScroll::townMapId))
                .orElse(null);
    }
}
