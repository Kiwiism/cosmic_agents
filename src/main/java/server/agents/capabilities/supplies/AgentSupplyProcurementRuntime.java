package server.agents.capabilities.supplies;

import client.Character;
import client.inventory.InventoryType;
import constants.id.ItemId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.capabilities.shop.AgentShopWorkflowPhase;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.progression.AgentCareerShopCatalog;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.events.AgentDomainEvent;
import server.agents.runtime.maintenance.AgentRemediationCoordinator;
import server.agents.runtime.maintenance.AgentRemediationFrame;
import server.agents.runtime.maintenance.AgentRemediationKind;
import server.agents.runtime.maintenance.AgentRemediationState;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Executes urgent supply requests as route-aware maintenance without destroying foreground intent. */
public final class AgentSupplyProcurementRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentSupplyProcurementRuntime.class);
    private static final String OBJECTIVE_PREFIX = "maintenance:resupply:";

    private AgentSupplyProcurementRuntime() {
    }

    /** Runs automatic maintenance only for explicitly self-sustaining sessions. */
    public static boolean tickIfSelfSustaining(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        boolean selfSustaining = entry.capabilityStates()
                .find(AgentResourceAutonomyState.STATE_KEY)
                .map(AgentResourceAutonomyState::selfSustaining)
                .orElse(false);
        if (!selfSustaining) {
            releaseInapplicableRestoredMaintenance(entry, agent, nowMs);
            return false;
        }
        return tick(entry, agent, nowMs);
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentResourcePlanningState planning = entry.capabilityStates().require(
                AgentResourcePlanningState.STATE_KEY);
        AgentSupplyProcurementState execution = entry.capabilityStates().require(
                AgentSupplyProcurementState.STATE_KEY);
        AgentSupplyMaintenanceEvaluationState evaluations = entry.capabilityStates().require(
                AgentSupplyMaintenanceEvaluationState.STATE_KEY);
        if (!execution.isActive()
                && releaseInapplicableRestoredMaintenance(
                entry, agent, planning, evaluations, nowMs)) {
            return false;
        }
        AgentSupplyThresholdChangedEvent signal = null;
        AgentProcurementRequest request;
        if (execution.isActive()) {
            if (!AgentSupplyRecoveryPolicy.requiresAutomaticReserve(
                    agent, execution.category())) {
                finish(entry, planning, execution, AgentObjectiveStatus.CANCELLED,
                        "beginner progression does not require a potion reserve", nowMs);
                return false;
            }
            request = planning.procurement(execution.category());
        } else {
            signal = evaluations.next();
            request = signal == null
                    ? null : selectRequest(planning, agent, nowMs, signal.category());
            if (signal != null && request == null) {
                evaluations.resolve(signal.category());
            }
            if (request == null) {
                request = selectRequest(planning, agent, nowMs, null);
            }
        }

        if (request == null) {
            if (!execution.isActive()) {
                return false;
            }
            stall(entry, agent, execution,
                    "supply request expired before targets were restored", nowMs);
            return true;
        }

        if (!execution.isActive()) {
            if (!begin(entry, agent, request, execution, nowMs)) {
                return false;
            }
            evaluations.resolve(request.category());
        }

        return switch (execution.phase()) {
            case TRAVEL_TO_SUPPLIER -> travelToSupplier(entry, agent, planning, execution, nowMs);
            case SHOPPING -> shop(entry, agent, planning, execution, nowMs);
            case RETURNING -> returnToPlan(entry, agent, planning, execution, nowMs);
            case RESTING -> restForRecovery(entry, agent, planning, execution, request, nowMs);
            case INCOME_RECOVERY -> recoverIncome(entry, agent, planning, execution, request, nowMs);
            case STALLED -> true;
            case IDLE -> false;
        };
    }

    private static boolean releaseInapplicableRestoredMaintenance(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        return releaseInapplicableRestoredMaintenance(
                entry,
                agent,
                entry.capabilityStates().require(AgentResourcePlanningState.STATE_KEY),
                entry.capabilityStates().require(AgentSupplyMaintenanceEvaluationState.STATE_KEY),
                nowMs);
    }

    private static boolean releaseInapplicableRestoredMaintenance(
            AgentRuntimeEntry entry,
            Character agent,
            AgentResourcePlanningState planning,
            AgentSupplyMaintenanceEvaluationState evaluations,
            long nowMs) {
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active == null || !"maintenance.resupply".equals(active.type())) {
            return false;
        }
        AgentResourceCategory category = java.util.Arrays.stream(
                        new AgentResourceCategory[]{
                                AgentResourceCategory.HP_POTION,
                                AgentResourceCategory.MP_POTION})
                .filter(candidate -> active.objectiveId().contains(':' + candidate.name() + ':'))
                .findFirst().orElse(null);
        if (category == null
                || AgentSupplyRecoveryPolicy.requiresAutomaticReserve(agent, category)) {
            return false;
        }
        planning.resolve(category);
        evaluations.resolve(category);
        return AgentObjectiveKernel.finishAndResume(
                entry, active.objectiveId(), AgentObjectiveStatus.CANCELLED,
                "beginner progression does not require a potion reserve", nowMs);
    }

    private static AgentProcurementRequest selectRequest(AgentResourcePlanningState planning,
                                                         Character agent,
                                                         long nowMs,
                                                         AgentResourceCategory category) {
        return planning.procurementSnapshot().values().stream()
                .filter(candidate -> candidate.expiresAtMs() >= nowMs)
                .filter(candidate -> candidate.urgency().ordinal() >= AgentSupplyUrgency.CRITICAL.ordinal())
                .filter(candidate -> candidate.permittedMethods().contains(AgentProcurementMethod.NPC_SHOP))
                .filter(candidate -> AgentSupplyRecoveryPolicy.requiresAutomaticReserve(
                        agent, candidate.category()))
                .filter(candidate -> category == null || candidate.category() == category)
                .max(Comparator.comparingInt(candidate -> candidate.urgency().ordinal()))
                .orElse(null);
    }

    static boolean begin(AgentRuntimeEntry entry,
                         Character agent,
                         AgentProcurementRequest request,
                         AgentSupplyProcurementState execution,
                         long nowMs) {
        AgentShopService.onMapChange(entry, agent, AgentInventoryGatewayRuntime.inventory());
        int supplierMapId = agent.getMapId();
        int supplierNpcId = 0;
        AgentSupplyProcurementState.Phase phase = AgentSupplyProcurementState.Phase.SHOPPING;
        if (!AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentCareerBuildBundle bundle = entry.capabilityStates().require(
                    AgentCareerProgressionState.STATE_KEY).bundle();
            if (bundle == null) {
                return false;
            }
            AgentCareerShopCatalog.ShopStop stop = AgentCareerShopCatalog.forSupply(
                    bundle, request.category(), agent.getMapId());
            supplierMapId = stop.mapId();
            supplierNpcId = stop.npcId();
            phase = supplierMapId == agent.getMapId()
                    ? AgentSupplyProcurementState.Phase.SHOPPING
                    : AgentSupplyProcurementState.Phase.TRAVEL_TO_SUPPLIER;
        }

        String maintenanceId = OBJECTIVE_PREFIX + request.requestId();
        AgentObjectiveDefinition maintenance = new AgentObjectiveDefinition(
                maintenanceId, "maintenance.resupply", 1_000, request.expiresAtMs(), 2,
                AgentObjectiveSource.RECOVERY_POLICY, "supply-procurement-v2",
                request.objectiveId().isBlank() ? maintenanceId : request.objectiveId());
        AgentObjectiveDefinition foreground = AgentObjectiveKernel.active(entry);
        String parentCorrelationId = foreground == null ? request.objectiveId()
                : foreground.correlationId();
        AgentRemediationFrame remediation = new AgentRemediationFrame(
                "resupply:" + request.requestId(),
                AgentRemediationKind.LOW_SUPPLIES,
                maintenanceId,
                parentCorrelationId == null ? "" : parentCorrelationId,
                1,
                nowMs,
                request.expiresAtMs(),
                Map.of("resourceCategory", request.category().name(),
                        "targetQuantity", Integer.toString(request.quantity())));
        if (!AgentRemediationCoordinator.begin(entry, remediation, maintenance,
                request.category() + " is " + request.urgency(), nowMs)) {
            return false;
        }
        execution.start(request.requestId(), maintenanceId, request.category(), supplierMapId,
                supplierNpcId, agent.getMapId(), phase,
                AgentSupplyInventorySnapshot.quantity(agent, request.category()),
                Math.max(0, agent.getMeso()));
        journal(entry, agent, request, "started", "supply shortage interrupted foreground work",
                Map.of("phase", phase.name(),
                        "sourceMapId", Integer.toString(agent.getMapId()),
                        "supplierMapId", Integer.toString(supplierMapId),
                        "quantityBefore", Integer.toString(execution.quantityBefore()),
                        "mesosBefore", Integer.toString(execution.mesosBefore())), nowMs);
        log.info("Agent '{}' suspended foreground work for {} resupply: phase={} sourceMap={} supplierMap={} supplierNpc={} existingShopVisit={}",
                agent.getName(), request.category(), phase, agent.getMapId(), supplierMapId,
                supplierNpcId, AgentShopStateRuntime.shopVisitPending(entry));
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            execution.markShopRequested();
        }
        return true;
    }

    private static boolean travelToSupplier(AgentRuntimeEntry entry,
                                            Character agent,
                                            AgentResourcePlanningState planning,
                                            AgentSupplyProcurementState execution,
                                            long nowMs) {
        AgentRouteOutcome outcome = AgentPrimitiveCapabilityGatewayRuntime.gateway().travelTo(
                entry, agent, execution.supplierMapId(), nowMs);
        if (outcome.status() == AgentRouteStatus.NO_ROUTE) {
            recordFailure(entry, agent, execution, AgentSupplyProcurementOutcome.Status.ROUTE_FAILED,
                    "no portal route reaches the selected supplier", nowMs);
            stall(entry, agent, execution,
                    "no portal route reaches the selected supplier", nowMs);
            return true;
        }
        if (outcome.status() != AgentRouteStatus.ARRIVED) {
            return true;
        }
        AgentShopStateRuntime.setShopSellTrashPending(entry, true);
        if (!AgentShopService.requestVisitAtNpc(entry, agent, execution.supplierNpcId(),
                AgentSupplyRecoveryPolicy.minimumWalletReserve(agent))) {
            recordFailure(entry, agent, execution, AgentSupplyProcurementOutcome.Status.SHOP_FAILED,
                    "selected supplier NPC is unavailable", nowMs);
            stall(entry, agent, execution,
                    "selected supplier NPC is unavailable", nowMs);
            return true;
        }
        execution.markShopRequested();
        return true;
    }

    private static boolean shop(AgentRuntimeEntry entry,
                                Character agent,
                                AgentResourcePlanningState planning,
                                AgentSupplyProcurementState execution,
                                long nowMs) {
        if (!execution.shopRequested()) {
            AgentShopStateRuntime.setShopSellTrashPending(entry, true);
            if (!AgentShopService.requestVisitAtNpc(entry, agent, execution.supplierNpcId(),
                    AgentSupplyRecoveryPolicy.minimumWalletReserve(agent))) {
                recordFailure(entry, agent, execution, AgentSupplyProcurementOutcome.Status.SHOP_FAILED,
                        "selected supplier NPC is unavailable", nowMs);
                stall(entry, agent, execution,
                        "selected supplier NPC is unavailable", nowMs);
                return true;
            }
            execution.markShopRequested();
        }
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentShopService.tickShopVisit(entry, agent, AgentInventoryGatewayRuntime.inventory());
            return true;
        }
        AgentShopWorkflowPhase phase = AgentShopStateRuntime.workflow(entry).phase();
        if (phase == AgentShopWorkflowPhase.COMPLETED) {
            AgentProcurementRequest request = planning.procurement(execution.category());
            int after = AgentSupplyInventorySnapshot.quantity(agent, execution.category());
            int target = request == null ? after : request.quantity() + execution.quantityBefore();
            AgentSupplyProcurementOutcome outcome = reconcile(
                    execution, agent, after, target, nowMs);
            execution.complete(outcome);
            journalOutcome(entry, agent, execution.requestId(), outcome);
            if (!outcome.restored()) {
                if (outcome.requiresRecoveryIncome()) {
                    return beginRecovery(entry, agent, execution, request, outcome, nowMs);
                }
                stall(entry, agent, execution, outcome.reason(), nowMs);
                return true;
            }
            execution.markReturning();
            return true;
        }
        if (phase == AgentShopWorkflowPhase.BLOCKED || phase == AgentShopWorkflowPhase.CANCELLED) {
            recordFailure(entry, agent, execution, AgentSupplyProcurementOutcome.Status.SHOP_FAILED,
                    "shop transaction ended in " + phase, nowMs);
            stall(entry, agent, execution,
                    "shop transaction ended in " + phase, nowMs);
            return true;
        }
        return true;
    }

    private static boolean returnToPlan(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentResourcePlanningState planning,
                                        AgentSupplyProcurementState execution,
                                        long nowMs) {
        AgentRouteOutcome outcome = AgentPrimitiveCapabilityGatewayRuntime.gateway().travelTo(
                entry, agent, execution.returnMapId(), nowMs);
        if (outcome.status() == AgentRouteStatus.NO_ROUTE) {
            recordFailure(entry, agent, execution, AgentSupplyProcurementOutcome.Status.ROUTE_FAILED,
                    "supplier visit completed but no return route reaches the suspended plan", nowMs);
            stall(entry, agent, execution,
                    "supplier visit completed but no return route reaches the suspended plan", nowMs);
            return true;
        }
        if (outcome.status() != AgentRouteStatus.ARRIVED) {
            return true;
        }
        finish(entry, planning, execution, AgentObjectiveStatus.SUCCEEDED,
                "shop transaction completed and plan location restored", nowMs);
        return false;
    }

    private static boolean beginRecovery(
            AgentRuntimeEntry entry,
            Character agent,
            AgentSupplyProcurementState execution,
            AgentProcurementRequest request,
            AgentSupplyProcurementOutcome outcome,
            long nowMs) {
        if (request == null) {
            stall(entry, agent, execution,
                    "supply request disappeared before recovery could begin", nowMs);
            return true;
        }
        if (execution.recoveryAttempts()
                >= AgentSupplyRecoveryPolicy.maximumRecoveryAttempts()) {
            stall(entry, agent, execution,
                    "resource recovery exhausted after " + execution.recoveryAttempts()
                            + " attempts: " + outcome.reason(), nowMs);
            return true;
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        AgentSupplyRecoveryPolicy.RecoveryMap map =
                AgentSupplyRecoveryPolicy.selectRecoveryMap(agent).orElse(null);
        execution.beginRecovery(map == null ? 0 : map.mapId(),
                Math.max(0, agent.getMeso()),
                AgentSupplyRecoveryPolicy.recoveryMesoTarget(agent, request),
                nowMs, nowMs + AgentSupplyRecoveryPolicy.restTimeoutMs());
        journal(entry, agent, request, "recovery-started",
                "shopping did not restore supplies; foreground remains suspended",
                Map.of("attempt", Integer.toString(execution.recoveryAttempts()),
                        "recoveryMapId", Integer.toString(execution.recoveryMapId()),
                        "mesoTarget", Integer.toString(execution.recoveryTargetMeso())), nowMs);
        return true;
    }

    private static boolean restForRecovery(
            AgentRuntimeEntry entry,
            Character agent,
            AgentResourcePlanningState planning,
            AgentSupplyProcurementState execution,
            AgentProcurementRequest request,
            long nowMs) {
        int chairId = ownedRecoveryChair(agent);
        boolean recovered = AgentSupplyRecoveryPolicy.recoveredForCombat(agent);
        if (!recovered && chairId > 0 && nowMs < execution.recoveryDeadlineAtMs()) {
            if (agent.getChair() != chairId) {
                AgentChairService.sit(entry, agent, chairId);
            }
            return true;
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        if (!recovered && AgentSupplyRecoveryPolicy.criticallyLowHp(agent)) {
            stall(entry, agent, execution,
                    chairId <= 0
                            ? "no owned recovery chair and HP is unsafe for income recovery"
                            : "chair recovery timed out below the safe combat threshold",
                    nowMs);
            return true;
        }
        if (execution.recoveryMapId() <= 0) {
            stall(entry, agent, execution,
                    "no bounded low-risk income map is available for this level and job",
                    nowMs);
            return true;
        }
        execution.markIncomeRecovery(
                nowMs, nowMs + AgentSupplyRecoveryPolicy.incomeTimeoutMs());
        if (request != null) {
            journal(entry, agent, request, "income-recovery",
                    "rest completed; starting bounded low-risk income recovery",
                    Map.of("attempt", Integer.toString(execution.recoveryAttempts()),
                            "mapId", Integer.toString(execution.recoveryMapId()),
                            "mesos", Integer.toString(Math.max(0, agent.getMeso()))), nowMs);
        }
        return true;
    }

    private static boolean recoverIncome(
            AgentRuntimeEntry entry,
            Character agent,
            AgentResourcePlanningState planning,
            AgentSupplyProcurementState execution,
            AgentProcurementRequest request,
            long nowMs) {
        if (AgentSupplyRecoveryPolicy.criticallyLowHp(agent)) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            execution.markResting(nowMs, nowMs + AgentSupplyRecoveryPolicy.restTimeoutMs());
            return true;
        }
        boolean targetReached = agent.getMeso() >= execution.recoveryTargetMeso();
        boolean timedOut = nowMs >= execution.recoveryDeadlineAtMs();
        if (targetReached || timedOut) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            if (timedOut
                    && agent.getMeso() <= execution.recoveryBaselineMeso()
                    && execution.recoveryAttempts()
                    >= AgentSupplyRecoveryPolicy.maximumRecoveryAttempts()) {
                stall(entry, agent, execution,
                        "bounded income recovery produced no mesos after "
                                + execution.recoveryAttempts() + " attempts", nowMs);
                return true;
            }
            execution.retrySupplier(agent.getMapId() == execution.supplierMapId());
            if (request != null) {
                journal(entry, agent, request, "shop-retry",
                        targetReached
                                ? "income target reached; retrying suspended resupply"
                                : "income window ended; retrying with recovered mesos",
                        Map.of("attempt", Integer.toString(execution.recoveryAttempts()),
                                "mesos", Integer.toString(Math.max(0, agent.getMeso())),
                                "targetReached", Boolean.toString(targetReached)), nowMs);
            }
            return true;
        }
        AgentSupplyRecoveryPolicy.RecoveryMap map = AgentSupplyRecoveryPolicy
                .recoveryMap(execution.recoveryMapId(), agent.getLevel()).orElse(null);
        if (map == null) {
            stall(entry, agent, execution,
                    "selected income recovery map is no longer eligible", nowMs);
            return true;
        }
        AgentRouteOutcome travel = AgentPrimitiveCapabilityGatewayRuntime.gateway().travelTo(
                entry, agent, map.mapId(), nowMs);
        if (travel.status() == AgentRouteStatus.NO_ROUTE) {
            stall(entry, agent, execution,
                    "no route reaches the bounded income recovery map " + map.mapId(), nowMs);
            return true;
        }
        if (travel.status() == AgentRouteStatus.ARRIVED) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(entry, map.mobIds());
        }
        return true;
    }

    private static int ownedRecoveryChair(Character agent) {
        if (agent == null || agent.getInventory(InventoryType.SETUP) == null) {
            return 0;
        }
        if (agent.getInventory(InventoryType.SETUP).countById(ItemId.RELAXER) > 0) {
            return ItemId.RELAXER;
        }
        return agent.getInventory(InventoryType.SETUP)
                .countById(ItemId.SKY_BLUE_WOODEN_CHAIR) > 0
                ? ItemId.SKY_BLUE_WOODEN_CHAIR : 0;
    }

    private static void stall(
            AgentRuntimeEntry entry,
            Character agent,
            AgentSupplyProcurementState execution,
            String reason,
            long nowMs) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        String failedPhase = execution.phase().name();
        execution.markStalled(reason);
        publish(entry, agent, "resource.recovery-stalled",
                "resource-stalled:" + execution.requestId() + ':' + nowMs,
                reason, Map.of(
                        "requestId", execution.requestId(),
                        "attempts", Integer.toString(execution.recoveryAttempts()),
                        "phase", failedPhase), nowMs);
        log.warn("Agent '{}' left foreground objective suspended after supply recovery stalled: {}",
                agent.getName(), reason);
    }

    private static void finish(AgentRuntimeEntry entry,
                               AgentResourcePlanningState planning,
                               AgentSupplyProcurementState execution,
                               AgentObjectiveStatus status,
                               String reason,
                               long nowMs) {
        String requestId = execution.requestId();
        String maintenanceObjectiveId = execution.objectiveId();
        var category = execution.category();
        execution.clear();
        if (category != null) {
            planning.resolve(category);
            entry.capabilityStates().require(AgentSupplyMaintenanceEvaluationState.STATE_KEY)
                    .resolve(category);
        }
        String frameId = "resupply:" + requestId;
        if (entry.capabilityStates().require(AgentRemediationState.STATE_KEY).active() == null) {
            AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
            String parentCorrelation = active == null ? "" : active.correlationId();
            AgentRemediationCoordinator.reattach(entry, new AgentRemediationFrame(
                    frameId, AgentRemediationKind.LOW_SUPPLIES, maintenanceObjectiveId,
                    parentCorrelation, 1, nowMs, Long.MAX_VALUE,
                    category == null ? Map.of() : Map.of("resourceCategory", category.name())));
        }
        AgentRemediationCoordinator.finish(entry, frameId, status, reason, nowMs);
    }

    private static AgentSupplyProcurementOutcome reconcile(
            AgentSupplyProcurementState execution,
            Character agent,
            int quantityAfter,
            int targetQuantity,
            long nowMs) {
        int mesosAfter = Math.max(0, agent.getMeso());
        AgentSupplyProcurementOutcome.Status status;
        String reason;
        if (quantityAfter >= targetQuantity) {
            status = AgentSupplyProcurementOutcome.Status.RESTORED;
            reason = "supply target restored after liquidation and purchase";
        } else if (quantityAfter > execution.quantityBefore()) {
            status = AgentSupplyProcurementOutcome.Status.PARTIALLY_RESTORED;
            reason = "shop improved supplies but did not restore the resume target";
        } else if (mesosAfter <= 0) {
            status = AgentSupplyProcurementOutcome.Status.INSUFFICIENT_MESO;
            reason = "safe liquidation produced insufficient mesos for the required supply";
        } else {
            status = AgentSupplyProcurementOutcome.Status.NO_PROGRESS;
            reason = "shop completed without increasing the required supply";
        }
        return new AgentSupplyProcurementOutcome(status, execution.category(),
                execution.quantityBefore(), quantityAfter, execution.mesosBefore(),
                mesosAfter, nowMs, reason);
    }

    private static void recordFailure(
            AgentRuntimeEntry entry,
            Character agent,
            AgentSupplyProcurementState execution,
            AgentSupplyProcurementOutcome.Status status,
            String reason,
            long nowMs) {
        AgentSupplyProcurementOutcome outcome = new AgentSupplyProcurementOutcome(
                status, execution.category(), execution.quantityBefore(),
                AgentSupplyInventorySnapshot.quantity(agent, execution.category()),
                execution.mesosBefore(), Math.max(0, agent.getMeso()), nowMs, reason);
        execution.complete(outcome);
        journalOutcome(entry, agent, execution.requestId(), outcome);
    }

    private static void journalOutcome(
            AgentRuntimeEntry entry,
            Character agent, String requestId, AgentSupplyProcurementOutcome outcome) {
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("status", outcome.status().name());
        evidence.put("category", outcome.category().name());
        evidence.put("quantityBefore", Integer.toString(outcome.quantityBefore()));
        evidence.put("quantityAfter", Integer.toString(outcome.quantityAfter()));
        evidence.put("mesosBefore", Integer.toString(outcome.mesosBefore()));
        evidence.put("mesosAfter", Integer.toString(outcome.mesosAfter()));
        publish(entry, agent, "resource.maintenance-outcome",
                "resource:" + requestId + ':' + outcome.occurredAtMs(),
                outcome.reason(), evidence, outcome.occurredAtMs());
    }

    private static void journal(
            AgentRuntimeEntry entry,
            Character agent,
            AgentProcurementRequest request,
            String phase,
            String reason,
            Map<String, String> facts,
            long nowMs) {
        Map<String, String> evidence = new LinkedHashMap<>(facts);
        evidence.put("phase", phase);
        evidence.put("category", request.category().name());
        evidence.put("targetShortfall", Integer.toString(request.quantity()));
        publish(entry, agent, "resource.maintenance-decision",
                "resource:" + request.requestId() + ':' + phase + ':' + nowMs,
                reason, evidence, nowMs);
    }

    private static void publish(
            AgentRuntimeEntry entry,
            Character agent,
            String type,
            String dedupeKey,
            String reason,
            Map<String, String> evidence,
            long nowMs) {
        Map<String, String> attributes = new LinkedHashMap<>(evidence);
        attributes.put("reason", reason == null ? "" : reason);
        AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(
                agent.getId(), nowMs, type, dedupeKey, attributes));
    }
}
