package server.agents.progression;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.events.AgentDomainEvent;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.Comparator;
import java.util.Map;

/** Typed bridge from a quest item requirement to the existing NPC-shop capability. */
final class AgentVictoriaQuestShopProcurementRuntime {
    private AgentVictoriaQuestShopProcurementRuntime() {
    }

    static Outcome tick(
            AgentRuntimeEntry entry,
            Character agent,
            int questId,
            AgentVictoriaQuestRuntimeCatalog.ShopProcurementObjective objective,
            boolean purchaseAttempted,
            PrimitiveCapabilityGateway gateway,
            long nowMs) {
        if (gateway.itemCount(agent, objective.targetId()) >= objective.requiredCount()) {
            return Outcome.complete();
        }
        AgentVictoriaQuestRuntimeCatalog.ShopSource source = selectSource(
                agent.getMapId(), objective);
        if (source == null) {
            return Outcome.failed("no reachable Victoria NPC vendor is cataloged");
        }
        AgentVictoriaRouteRuntime.TravelOutcome travel = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, source.mapId(), gateway, nowMs);
        if (travel.status() == AgentVictoriaRouteRuntime.Status.NO_ROUTE) {
            return Outcome.failed("no route to quest-item vendor map " + source.mapId());
        }
        if (travel.status() != AgentVictoriaRouteRuntime.Status.ARRIVED
                || AgentShopStateRuntime.shopVisitPending(entry)) {
            return Outcome.running(false);
        }
        if (purchaseAttempted) {
            String detail = AgentShopStateRuntime.workflow(entry).reason();
            return Outcome.failed(detail == null || detail.isBlank()
                    ? "NPC shop did not supply the required quest item"
                    : "NPC shop did not supply the required quest item: " + detail);
        }
        boolean started = AgentShopService.requestVisitAtNpc(
                entry, agent, source.npcId(), 0,
                objective.targetId(), objective.requiredCount());
        if (!started) {
            return Outcome.failed("cataloged quest-item vendor is unavailable on map "
                    + source.mapId());
        }
        AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                agent.getId(), nowMs, "progression.quest-shop-procurement",
                "quest-shop:" + agent.getId() + ':' + questId + ':'
                        + objective.targetId() + ':' + nowMs,
                Map.of("questId", Integer.toString(questId),
                        "objectiveId", objective.objectiveId(),
                        "itemId", Integer.toString(objective.targetId()),
                        "requiredCount", Integer.toString(objective.requiredCount()),
                        "npcId", Integer.toString(source.npcId()),
                        "mapId", Integer.toString(source.mapId()),
                        "unitPrice", Integer.toString(source.unitPrice()),
                        "reason", "buy required quest item from nearest reachable NPC vendor")));
        return Outcome.running(true);
    }

    static AgentVictoriaQuestRuntimeCatalog.ShopSource selectSource(
            int currentMapId,
            AgentVictoriaQuestRuntimeCatalog.ShopProcurementObjective objective) {
        return objective.shopSources().stream()
                .filter(source -> AgentVictoriaTrainingRouteCatalog.canRoute(
                        currentMapId, source.mapId()))
                .min(Comparator
                        .comparingInt((AgentVictoriaQuestRuntimeCatalog.ShopSource source) ->
                                AgentVictoriaTrainingRouteCatalog.distance(
                                        currentMapId, source.mapId()))
                        .thenComparingInt(AgentVictoriaQuestRuntimeCatalog.ShopSource::unitPrice)
                        .thenComparingInt(AgentVictoriaQuestRuntimeCatalog.ShopSource::mapId)
                        .thenComparingInt(AgentVictoriaQuestRuntimeCatalog.ShopSource::npcId))
                .orElse(null);
    }

    enum Status { RUNNING, COMPLETE, FAILED }

    record Outcome(Status status, boolean purchaseStarted, String reason) {
        private static Outcome running(boolean purchaseStarted) {
            return new Outcome(Status.RUNNING, purchaseStarted, "");
        }

        private static Outcome complete() {
            return new Outcome(Status.COMPLETE, false, "");
        }

        private static Outcome failed(String reason) {
            return new Outcome(Status.FAILED, false, reason == null ? "" : reason.trim());
        }
    }
}
