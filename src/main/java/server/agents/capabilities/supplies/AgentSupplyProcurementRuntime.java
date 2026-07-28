package server.agents.capabilities.supplies;

import client.Character;
import server.agents.capabilities.contracts.AgentProcurementMethod;
import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.capabilities.shop.AgentShopWorkflowPhase;
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
import server.agents.runtime.maintenance.AgentRemediationCoordinator;
import server.agents.runtime.maintenance.AgentRemediationFrame;
import server.agents.runtime.maintenance.AgentRemediationKind;
import server.agents.runtime.maintenance.AgentRemediationState;

import java.util.Comparator;
import java.util.Map;

/** Executes urgent supply requests as route-aware maintenance without destroying foreground intent. */
public final class AgentSupplyProcurementRuntime {
    private static final String OBJECTIVE_PREFIX = "maintenance:resupply:";

    private AgentSupplyProcurementRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentResourcePlanningState planning = entry.capabilityStates().require(
                AgentResourcePlanningState.STATE_KEY);
        AgentSupplyProcurementState execution = entry.capabilityStates().require(
                AgentSupplyProcurementState.STATE_KEY);
        AgentSupplyMaintenanceEvaluationState evaluations = entry.capabilityStates().require(
                AgentSupplyMaintenanceEvaluationState.STATE_KEY);
        AgentSupplyThresholdChangedEvent signal = execution.isActive() ? null : evaluations.next();
        AgentProcurementRequest request = signal == null
                ? null : selectRequest(planning, nowMs, signal.category());
        if (signal != null && request == null) {
            evaluations.resolve(signal.category());
        }
        if (request == null) {
            request = selectRequest(planning, nowMs, null);
        }

        if (request == null) {
            if (!execution.isActive()) {
                return false;
            }
            finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                    "supply request expired before targets were restored", nowMs);
            return false;
        }

        if (!execution.isActive()) {
            if (!begin(entry, agent, request, execution, nowMs)) {
                return false;
            }
            evaluations.resolve(request.category());
        } else if (!execution.requestId().equals(request.requestId())) {
            return true;
        }

        return switch (execution.phase()) {
            case TRAVEL_TO_SUPPLIER -> travelToSupplier(entry, agent, planning, execution, nowMs);
            case SHOPPING -> shop(entry, agent, planning, execution, nowMs);
            case RETURNING -> returnToPlan(entry, agent, planning, execution, nowMs);
            case IDLE -> false;
        };
    }

    private static AgentProcurementRequest selectRequest(AgentResourcePlanningState planning,
                                                         long nowMs,
                                                         AgentResourceCategory category) {
        return planning.procurementSnapshot().values().stream()
                .filter(candidate -> candidate.expiresAtMs() >= nowMs)
                .filter(candidate -> candidate.urgency().ordinal() >= AgentSupplyUrgency.CRITICAL.ordinal())
                .filter(candidate -> candidate.permittedMethods().contains(AgentProcurementMethod.NPC_SHOP))
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
            AgentCareerShopCatalog.ShopStop stop = AgentCareerShopCatalog.forBundle(bundle);
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
                supplierNpcId, agent.getMapId(), phase);
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
            finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                    "no portal route reaches the selected supplier", nowMs);
            return false;
        }
        if (outcome.status() != AgentRouteStatus.ARRIVED) {
            return true;
        }
        if (!AgentShopService.requestVisitAtNpc(entry, agent, execution.supplierNpcId())) {
            finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                    "selected supplier NPC is unavailable", nowMs);
            return false;
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
            if (!AgentShopService.requestVisitAtNpc(entry, agent, execution.supplierNpcId())) {
                finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                        "selected supplier NPC is unavailable", nowMs);
                return false;
            }
            execution.markShopRequested();
        }
        if (AgentShopStateRuntime.shopVisitPending(entry)) {
            AgentShopService.tickShopVisit(entry, agent, AgentInventoryGatewayRuntime.inventory());
            return true;
        }
        AgentShopWorkflowPhase phase = AgentShopStateRuntime.workflow(entry).phase();
        if (phase == AgentShopWorkflowPhase.COMPLETED) {
            execution.markReturning();
            return true;
        }
        if (phase == AgentShopWorkflowPhase.BLOCKED || phase == AgentShopWorkflowPhase.CANCELLED) {
            finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                    "shop transaction ended in " + phase, nowMs);
            return false;
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
            finish(entry, planning, execution, AgentObjectiveStatus.FAILED,
                    "supplier visit completed but no return route reaches the suspended plan", nowMs);
            return false;
        }
        if (outcome.status() != AgentRouteStatus.ARRIVED) {
            return true;
        }
        finish(entry, planning, execution, AgentObjectiveStatus.SUCCEEDED,
                "shop transaction completed and plan location restored", nowMs);
        return false;
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
}
