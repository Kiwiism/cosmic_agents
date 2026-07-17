package server.agents.plans.amherst;

import client.Character;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AmherstObjectiveReconciler {
    public record Decision(boolean satisfied, String reason) {
    }

    private final PrimitiveCapabilityGateway gateway;

    public AmherstObjectiveReconciler() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AmherstObjectiveReconciler(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    public Decision reconcile(AmherstPlanCard card, AmherstPlanObjective objective, Character agent) {
        boolean satisfied = switch (objective.kind()) {
            case QUEST_START -> gateway.questStatus(agent, objective.questId()) >= 1;
            case QUEST_COMPLETE -> gateway.questStatus(agent, objective.questId()) == 2;
            case FORCE_COMPLETE_QUEST -> gateway.questStatus(agent, objective.questId()) == 2;
            case QUEST_CHAIN -> objective.questIds().stream().allMatch(questId ->
                    gateway.questStatus(agent, questId) == 2);
            case QUEST_CHAIN_IF_AVAILABLE -> objective.questIds().stream().allMatch(questId ->
                    optionalQuestSatisfied(agent, questId));
            case USE_ITEM -> gateway.questStatus(agent, objective.questId()) == 2
                    || gateway.questStatus(agent, objective.questId()) == 1
                    && gateway.itemCount(agent, objective.itemId()) == 0;
            case KILL_MOBS -> gateway.questStatus(agent, objective.questId()) == 2
                    || killsSatisfied(agent, objective);
            case REACTOR_HIT, REACTOR_BOX_ITEMS -> gateway.questStatus(agent, objective.questId()) == 2
                    || reactorItemsSatisfied(card, objective, agent);
            case STOP_PLAN -> gateway.mapId(agent) == card.exitCriteria().finalMapId()
                    && card.requiredQuestIds().stream().allMatch(questId ->
                    gateway.questStatus(agent, questId)
                            == (card.exitCriteria().startOnlyQuestIds().contains(questId) ? 1 : 2))
                    && card.exitCriteria().blockedCompletedQuestIds().stream().noneMatch(questId ->
                    gateway.questStatus(agent, questId) == 2);
        };
        return new Decision(satisfied, satisfied
                ? "authoritative live state satisfies objective"
                : "authoritative live state requires objective execution");
    }

    private boolean optionalQuestSatisfied(Character agent, int questId) {
        int status = gateway.questStatus(agent, questId);
        if (status == 2) {
            return true;
        }
        if (status == 1) {
            return false;
        }
        return MapleIslandSouthperryQuestCatalog.findAny(questId)
                .map(definition -> !gateway.canStartQuest(agent, questId, definition.startNpc().id()))
                .orElse(false);
    }

    private boolean killsSatisfied(Character agent, AmherstPlanObjective objective) {
        for (int i = 0; i < objective.mobIds().size(); i++) {
            if (gateway.questProgress(agent, objective.questId(), objective.mobIds().get(i))
                    < objective.counts().get(i)) {
                return false;
            }
        }
        return itemCounts(objective.itemIds()).entrySet().stream().allMatch(required ->
                gateway.itemCount(agent, required.getKey()) >= required.getValue());
    }

    private boolean reactorItemsSatisfied(AmherstPlanCard card,
                                          AmherstPlanObjective objective,
                                          Character agent) {
        var itemIds = objective.itemIds().isEmpty()
                ? card.objectives().stream()
                .filter(candidate -> candidate.kind() == AmherstPlanObjectiveKind.REACTOR_BOX_ITEMS)
                .filter(candidate -> candidate.questId().equals(objective.questId()))
                .findFirst().map(AmherstPlanObjective::itemIds).orElse(java.util.List.of())
                : objective.itemIds();
        return !itemIds.isEmpty() && itemCounts(itemIds).entrySet().stream().allMatch(required ->
                gateway.itemCount(agent, required.getKey()) >= required.getValue());
    }

    private static Map<Integer, Integer> itemCounts(List<Integer> itemIds) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        itemIds.forEach(itemId -> counts.merge(itemId, 1, Integer::sum));
        return Map.copyOf(counts);
    }
}
