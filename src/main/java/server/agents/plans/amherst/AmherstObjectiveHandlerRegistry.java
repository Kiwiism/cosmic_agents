package server.agents.plans.amherst;

import server.agents.capabilities.objective.CombatQuestObjectiveCapability;
import server.agents.capabilities.objective.ForceCompleteQuestObjectiveCapability;
import server.agents.capabilities.objective.InventoryUseObjectiveCapability;
import server.agents.capabilities.objective.AmherstNpcInteractionDelay;
import server.agents.capabilities.objective.NpcQuestObjectiveCapability;
import server.agents.capabilities.objective.PlanStopObjectiveCapability;
import server.agents.capabilities.objective.ReactorLootObjectiveCapability;
import server.agents.capabilities.movement.AgentRelaxerSpotCatalog;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import constants.id.ItemId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AmherstObjectiveHandlerRegistry {
    private static final long OBJECTIVE_TIMEOUT_MS = 300_000L;
    private static final int OBJECTIVE_RETRIES = 1;
    private static final String RELAXER_MODE = "relaxer";
    private static final String SOUTHPERRY_RELAXER_MODE = "southperry-relaxer";
    private static final String SOUTHPERRY_LEFT_RELAXER_MODE = "southperry-left-relaxer";
    private static final String SOUTHPERRY_RIGHT_RELAXER_MODE = "southperry-right-relaxer";

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstNpcInteractionDelay npcInteractionDelay;
    private final AmherstScopePolicy scopePolicy;

    public AmherstObjectiveHandlerRegistry() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), AmherstNpcInteractionDelay.NONE,
                new AmherstScopePolicy());
    }

    public AmherstObjectiveHandlerRegistry(PrimitiveCapabilityGateway gateway) {
        this(gateway, AmherstNpcInteractionDelay.NONE, new AmherstScopePolicy());
    }

    public AmherstObjectiveHandlerRegistry(PrimitiveCapabilityGateway gateway,
                                            AmherstNpcInteractionDelay npcInteractionDelay) {
        this(gateway, npcInteractionDelay, new AmherstScopePolicy());
    }

    public AmherstObjectiveHandlerRegistry(PrimitiveCapabilityGateway gateway,
                                            AmherstNpcInteractionDelay npcInteractionDelay,
                                            AmherstScopePolicy scopePolicy) {
        this.gateway = gateway;
        this.npcInteractionDelay = npcInteractionDelay == null
                ? AmherstNpcInteractionDelay.NONE : npcInteractionDelay;
        this.scopePolicy = scopePolicy;
    }

    public AmherstObjectiveExecution create(AmherstPlanCard card, AmherstPlanObjective objective) {
        return switch (objective.kind()) {
            case QUEST_START -> npcQuest(objective, List.of(operation(objective.questId(), 1,
                    objective.npcId(), null)), false);
            case QUEST_COMPLETE -> npcQuest(objective, List.of(operation(objective.questId(), 2,
                    null, objective.npcId())), false);
            case FORCE_COMPLETE_QUEST -> execution(objective.objectiveId(),
                    new ForceCompleteQuestObjectiveCapability(gateway, scopePolicy, npcInteractionDelay),
                    new ForceCompleteQuestObjectiveCapability.Command(
                            objective.objectiveId(), objective.mapId(), objective.questId(), objective.npcId()));
            case QUEST_CHAIN -> npcQuest(objective, chainOperations(objective, 2), false);
            case QUEST_CHAIN_IF_AVAILABLE -> npcQuest(objective, chainOperations(objective, 2), true);
            case USE_ITEM -> execution(objective.objectiveId(), new InventoryUseObjectiveCapability(gateway),
                    new InventoryUseObjectiveCapability.Command(
                            objective.objectiveId(), objective.questId(), objective.itemId()));
            case KILL_MOBS -> execution(objective.objectiveId(),
                    new CombatQuestObjectiveCapability(gateway, scopePolicy),
                    new CombatQuestObjectiveCapability.Command(objective.objectiveId(), objective.mapId(),
                            objective.questId(), zip(objective.mobIds(), objective.counts()),
                            itemCounts(objective.itemIds())));
            case REACTOR_HIT, REACTOR_BOX_ITEMS -> execution(objective.objectiveId(),
                    new ReactorLootObjectiveCapability(gateway),
                    new ReactorLootObjectiveCapability.Command(objective.objectiveId(), objective.mapId(),
                            objective.questId(), null, null, reactorItems(card, objective), null));
            case STOP_PLAN -> execution(objective.objectiveId(),
                    new PlanStopObjectiveCapability(gateway, scopePolicy),
                    new PlanStopObjectiveCapability.Command(objective.objectiveId(),
                            card.exitCriteria().finalMapId(), expectedQuestStatuses(card),
                            card.exitCriteria().blockedCompletedQuestIds(), objective.reason(),
                            isRelaxerMode(objective.mode()) ? ItemId.RELAXER : null,
                            restSpotPool(objective.mode())));
        };
    }

    private AmherstObjectiveExecution npcQuest(AmherstPlanObjective objective,
                                                List<NpcQuestObjectiveCapability.QuestOperation> operations,
                                                boolean skipUnavailable) {
        return execution(objective.objectiveId(), new NpcQuestObjectiveCapability(
                        gateway, scopePolicy, npcInteractionDelay),
                new NpcQuestObjectiveCapability.Command(
                        objective.objectiveId(), objective.mapId(), operations, skipUnavailable));
    }

    private List<NpcQuestObjectiveCapability.QuestOperation> chainOperations(AmherstPlanObjective objective,
                                                                             int desiredStatus) {
        List<NpcQuestObjectiveCapability.QuestOperation> operations = new ArrayList<>();
        for (Integer questId : objective.questIds()) {
            operations.add(operation(questId, desiredStatus, null, null));
        }
        return List.copyOf(operations);
    }

    private NpcQuestObjectiveCapability.QuestOperation operation(int questId,
                                                                 int desiredStatus,
                                                                 Integer startOverride,
                                                                 Integer completeOverride) {
        var definition = MapleIslandSouthperryQuestCatalog.findAny(questId)
                .orElseThrow(() -> new IllegalArgumentException("quest is not in a Maple Island catalog: " + questId));
        int startNpc = startOverride == null ? definition.startNpc().id() : startOverride;
        int completeNpc = completeOverride == null ? definition.completeNpc().id() : completeOverride;
        if (completeNpc <= 0) {
            completeNpc = startNpc;
        }
        return new NpcQuestObjectiveCapability.QuestOperation(
                questId, startNpc, completeNpc, desiredStatus);
    }

    private Map<Integer, Integer> reactorItems(AmherstPlanCard card, AmherstPlanObjective objective) {
        List<Integer> itemIds = objective.itemIds();
        if (itemIds.isEmpty()) {
            itemIds = card.objectives().stream()
                    .filter(candidate -> candidate.kind() == AmherstPlanObjectiveKind.REACTOR_BOX_ITEMS)
                    .filter(candidate -> candidate.questId().equals(objective.questId()))
                    .findFirst()
                    .map(AmherstPlanObjective::itemIds)
                    .orElse(List.of());
        }
        if (itemIds.isEmpty()) {
            throw new IllegalArgumentException("reactor objective has no declared item postcondition");
        }
        Map<Integer, Integer> items = new LinkedHashMap<>();
        itemIds.forEach(itemId -> items.merge(itemId, 1, Integer::sum));
        return Map.copyOf(items);
    }

    private static Map<Integer, Integer> zip(List<Integer> keys, List<Integer> values) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            result.put(keys.get(i), values.get(i));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Integer> itemCounts(List<Integer> itemIds) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        itemIds.forEach(itemId -> result.merge(itemId, 1, Integer::sum));
        return Map.copyOf(result);
    }

    private static Map<Integer, Integer> expectedQuestStatuses(AmherstPlanCard card) {
        Map<Integer, Integer> statuses = new LinkedHashMap<>();
        for (Integer questId : card.requiredQuestIds()) {
            statuses.put(questId, card.exitCriteria().startOnlyQuestIds().contains(questId) ? 1 : 2);
        }
        return Map.copyOf(statuses);
    }

    private static boolean isRelaxerMode(String mode) {
        return restSpotPool(mode) != null;
    }

    private static AgentRelaxerSpotCatalog.Pool restSpotPool(String mode) {
        if (RELAXER_MODE.equalsIgnoreCase(mode)) {
            return AgentRelaxerSpotCatalog.Pool.AMHERST;
        }
        if (SOUTHPERRY_RELAXER_MODE.equalsIgnoreCase(mode)) {
            return AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_ALL;
        }
        if (SOUTHPERRY_LEFT_RELAXER_MODE.equalsIgnoreCase(mode)) {
            return AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_LEFT;
        }
        if (SOUTHPERRY_RIGHT_RELAXER_MODE.equalsIgnoreCase(mode)) {
            return AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT;
        }
        return null;
    }

    private static <C extends AgentCapabilityCommand> AmherstObjectiveExecution execution(
            String objectiveId, AgentExecutableCapability<C> capability, C command) {
        return new AmherstObjectiveExecution(objectiveId,
                new AgentCapabilityInvocation<>(capability, command, OBJECTIVE_TIMEOUT_MS, OBJECTIVE_RETRIES));
    }
}
