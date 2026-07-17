package server.agents.capabilities.objective;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.navigation.AgentPortalRoutePolicy;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.capabilities.npc.AgentNpcInteractionPolicy;
import server.agents.capabilities.primitive.AgentCombatCapability;
import server.agents.capabilities.primitive.AgentFinalStateVerificationCapability;
import server.agents.capabilities.primitive.AgentInventoryInspectionCapability;
import server.agents.capabilities.primitive.AgentItemUseCapability;
import server.agents.capabilities.primitive.AgentLootCapability;
import server.agents.capabilities.primitive.AgentNavigationCapability;
import server.agents.capabilities.primitive.AgentNpcInteractionPrimitiveCapability;
import server.agents.capabilities.primitive.AgentPortalTravelCapability;
import server.agents.capabilities.primitive.AgentQuestCompletePrimitiveCapability;
import server.agents.capabilities.primitive.AgentQuestStartPrimitiveCapability;
import server.agents.capabilities.primitive.AgentQuestStateCapability;
import server.agents.capabilities.primitive.AgentReactorPrimitiveCapability;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.reactor.AgentReactorInteractionMode;
import server.agents.capabilities.reactor.AgentReactorInteractionRequest;
import server.agents.capabilities.reactor.AgentReactorTargetSelector;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AmherstObjectiveCapabilitySupport {
    static final long CHILD_TIMEOUT_MS = 30_000L;
    static final long COMBAT_TIMEOUT_MS = 180_000L;
    static final long PORTAL_TIMEOUT_MS = 90_000L;
    static final int CHILD_RETRIES = 2;
    static final int NPC_RANGE_PX = AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX;
    static final int NPC_ANCHOR_ARRIVAL_RANGE_PX = 8;
    static final int REACTOR_RANGE_PX = 60;

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstScopePolicy scopePolicy;
    private final AmherstNpcInteractionDelay npcInteractionDelay;
    private final AgentPortalRoutePolicy portalRoutePolicy;

    AmherstObjectiveCapabilitySupport() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy(),
                AmherstNpcInteractionDelay.NONE, AgentPortalRoutePolicy.DIRECT);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway) {
        this(gateway, new AmherstScopePolicy(), AmherstNpcInteractionDelay.NONE,
                AgentPortalRoutePolicy.DIRECT);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway, AmherstScopePolicy scopePolicy) {
        this(gateway, scopePolicy, AmherstNpcInteractionDelay.NONE, AgentPortalRoutePolicy.DIRECT);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway,
                                      AmherstScopePolicy scopePolicy,
                                      AmherstNpcInteractionDelay npcInteractionDelay) {
        this(gateway, scopePolicy, npcInteractionDelay, AgentPortalRoutePolicy.DIRECT);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway,
                                      AmherstScopePolicy scopePolicy,
                                      AmherstNpcInteractionDelay npcInteractionDelay,
                                      AgentPortalRoutePolicy portalRoutePolicy) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
        this.npcInteractionDelay = npcInteractionDelay == null
                ? AmherstNpcInteractionDelay.NONE : npcInteractionDelay;
        this.portalRoutePolicy = portalRoutePolicy == null
                ? AgentPortalRoutePolicy.DIRECT : portalRoutePolicy;
    }

    PrimitiveCapabilityGateway gateway() {
        return gateway;
    }

    AgentCapabilityStep propagateChildFailure(AgentCapabilityContext context) {
        AgentCapabilityResult child = context.childResult();
        return child != null && child.status() != AgentCapabilityStatus.SUCCESS
                ? AgentCapabilityStep.terminal(child) : null;
    }

    AgentCapabilityStep travel(AgentCapabilityContext context, int destinationMapId) {
        int sourceMapId = gateway.mapId(context.agent());
        if (sourceMapId == destinationMapId) {
            return null;
        }
        var scope = scopePolicy.checkMap(destinationMapId);
        if (!scope.allowed()) {
            return blocked(scope.status(), scope.reason());
        }
        Integer portalId = gateway.directPortalIdTo(context.agent(), destinationMapId);
        if (portalId == null) {
            Integer nextHopMapId = scopePolicy.nextHopMap(sourceMapId, destinationMapId);
            if (nextHopMapId == null) {
                return blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                        "no in-scope portal route reaches objective map " + destinationMapId);
            }
            portalId = gateway.directPortalIdTo(context.agent(), nextHopMapId);
            if (portalId == null) {
                portalId = scopePolicy.scriptedPortalId(sourceMapId, nextHopMapId);
            }
            if (portalId == null) {
                return blocked(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                        "expected in-scope portal to map " + nextHopMapId + " is unavailable");
            }
            destinationMapId = nextHopMapId;
        }
        return AgentCapabilityStep.handoff(invocation(
                new AgentPortalTravelCapability(gateway, scopePolicy),
                new AgentPortalTravelCapability.Command(sourceMapId, portalId, destinationMapId, true,
                        portalRoutePolicy.plan(context.entry(), sourceMapId, destinationMapId)),
                PORTAL_TIMEOUT_MS),
                "objective requests portal travel");
    }

    AgentCapabilityStep approachNpc(AgentCapabilityContext context, int mapId, int npcId) {
        return approachNpc(context, mapId, npcId, AgentNpcInteractionPlacementData.direct(NPC_RANGE_PX));
    }

    AgentCapabilityStep approachNpc(AgentCapabilityContext context, int mapId, int npcId,
                                    AgentNpcInteractionPlacementData placementData) {
        var npcScope = scopePolicy.checkNpcTravel(npcId);
        if (!npcScope.allowed()) {
            return blocked(npcScope.status(), npcScope.reason());
        }
        AgentCapabilityStep travel = travel(context, mapId);
        if (travel != null) {
            return travel;
        }
        Point npcPosition = gateway.npcPosition(context.agent(), npcId);
        if (npcPosition == null) {
            return missing("objective NPC is not present on map " + mapId);
        }
        Point currentPosition = gateway.position(context.agent());
        AgentNpcInteractionPlacementPolicy.Placement placement = interactionPlacement(
                context, mapId, npcId, currentPosition, npcPosition, placementData);
        int interactionRangePx = placement.interactionRangePx();
        context.memory().putInt("npcInteractionRangePx", interactionRangePx);
        Point interactionAnchor = placement.anchor();
        boolean climbableAnchor = placement.climbable();
        if (interactionAnchor != null
                && currentPosition.distanceSq(interactionAnchor)
                > (long) NPC_ANCHOR_ARRIVAL_RANGE_PX * NPC_ANCHOR_ARRIVAL_RANGE_PX) {
            return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                    new AgentNavigationCapability.Command(
                            mapId, interactionAnchor, NPC_ANCHOR_ARRIVAL_RANGE_PX, true,
                            climbableAnchor)),
                    "objective requests navigation to safe NPC interaction anchor");
        }
        if (currentPosition.distanceSq(npcPosition)
                <= (long) interactionRangePx * interactionRangePx) {
            if (!gateway.grounded(context.agent())
                    && !matchesClimbingAnchor(context, interactionAnchor, climbableAnchor)) {
                return AgentCapabilityStep.running("waiting to land before NPC interaction", false);
            }
            gateway.facePosition(context.agent(), npcPosition);
            return null;
        }
        return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(mapId, npcPosition, interactionRangePx, true)),
                "objective requests navigation to NPC");
    }

    private boolean matchesClimbingAnchor(AgentCapabilityContext context,
                                          Point anchor,
                                          boolean climbableAnchor) {
        // Navigation already required an active climb state before reporting
        // arrival. Its terminal stop clears that transient state, so validate
        // the remembered anchor against the map instead of requiring the state
        // to survive across the child-to-parent handoff.
        return climbableAnchor && anchor != null
                && AgentNpcInteractionSpreadService.isClimbableAnchor(context.agent(), anchor);
    }

    private AgentNpcInteractionPlacementPolicy.Placement interactionPlacement(
            AgentCapabilityContext context,
            int mapId,
            int npcId,
            Point currentPosition,
            Point npcPosition,
            AgentNpcInteractionPlacementData data) {
        if (context.memory().intValue("npcAnchorMapId", -1) == mapId
                && context.memory().intValue("npcAnchorNpcId", -1) == npcId
                && context.memory().booleanValue("npcAnchorSelected", false)) {
            return new AgentNpcInteractionPlacementPolicy.Placement(
                    context.memory().intValue("npcInteractionRangePx", NPC_RANGE_PX),
                    new Point(context.memory().intValue("npcAnchorX", 0),
                            context.memory().intValue("npcAnchorY", 0)),
                    context.memory().booleanValue("npcAnchorClimbable", false));
        }
        AgentNpcInteractionPlacementPolicy.Placement placement = selectPlacement(
                context, mapId, npcId, currentPosition, npcPosition, data);
        Point anchor = placement.anchor();
        if (anchor == null) {
            return placement;
        }
        context.memory().putInt("npcAnchorMapId", mapId);
        context.memory().putInt("npcAnchorNpcId", npcId);
        context.memory().putInt("npcAnchorX", anchor.x);
        context.memory().putInt("npcAnchorY", anchor.y);
        context.memory().putInt("npcInteractionRangePx", placement.interactionRangePx());
        context.memory().putBoolean("npcAnchorClimbable", placement.climbable());
        context.memory().putBoolean("npcAnchorSelected", true);
        return placement;
    }

    private AgentNpcInteractionPlacementPolicy.Placement selectPlacement(
            AgentCapabilityContext context, int mapId, int npcId,
            Point currentPosition, Point npcPosition, AgentNpcInteractionPlacementData data) {
        AgentNpcInteractionPlacementData resolved = data == null
                ? AgentNpcInteractionPlacementData.direct(NPC_RANGE_PX) : data;
        Point placementCenter = new Point(
                npcPosition.x + resolved.placementCenterOffset().x,
                npcPosition.y + resolved.placementCenterOffset().y);
        List<Point> curated = resolved.anchors().stream()
                .filter(candidate -> candidate.distanceSq(placementCenter)
                        <= (long) resolved.placementRadiusPx() * resolved.placementRadiusPx())
                .toList();
        List<Point> spread = resolved.dynamicSpread()
                ? AgentNpcInteractionSpreadService.candidates(context.agent(), currentPosition,
                placementCenter, resolved.placementRadiusPx()) : List.of();
        List<Point> candidates = spread.size() >= 2
                ? AgentNpcInteractionSpreadService.selectionPool(spread,
                resolved.trafficBiasX() == null ? currentPosition
                        : new Point(npcPosition.x + resolved.trafficBiasX(), npcPosition.y))
                : curated;
        var selected = AgentObjectiveVariationRuntime.selectNpcAnchorIndex(
                context.entry(), mapId, npcId, candidates.size());
        Point anchor = selected.isPresent() ? candidates.get(selected.getAsInt())
                : resolved.legacyAnchors().stream()
                .min(java.util.Comparator.comparingDouble(currentPosition::distanceSq))
                .map(Point::new).orElse(null);
        return new AgentNpcInteractionPlacementPolicy.Placement(resolved.interactionRangePx(), anchor,
                AgentNpcInteractionSpreadService.isClimbableAnchor(context.agent(), anchor));
    }

    AgentCapabilityStep approachPosition(AgentCapabilityContext context,
                                         int mapId,
                                         Point destination,
                                         int arrivalRangePx) {
        AgentCapabilityStep travel = travel(context, mapId);
        if (travel != null) {
            return travel;
        }
        if (gateway.position(context.agent()).distanceSq(destination)
                <= (long) arrivalRangePx * arrivalRangePx) {
            return gateway.grounded(context.agent())
                    ? null
                    : AgentCapabilityStep.running("waiting to land at destination", false);
        }
        return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(mapId, destination, arrivalRangePx, true)),
                "objective requests navigation to field position");
    }

    boolean waitForNpcInteraction(AgentCapabilityContext context, int operationIndex) {
        return waitForNpcInteraction(context, operationIndex, 0);
    }

    boolean waitForNpcInteraction(AgentCapabilityContext context,
                                  int operationIndex,
                                  int interactionStage) {
        return waitForNpcInteraction(context, operationIndex, interactionStage, false);
    }

    boolean waitForNpcInteraction(AgentCapabilityContext context,
                                  int operationIndex,
                                  int interactionStage,
                                  boolean distinguishInteractionStages) {
        String operationKey = "npcDelayOperation";
        String readyAtKey = "npcReadyAtMs";
        int delayOperation = distinguishInteractionStages
                ? 31 * operationIndex + interactionStage : operationIndex;
        if (context.memory().intValue(operationKey, -1) != delayOperation) {
            long delayMs = Math.max(0L, npcInteractionDelay.nextDelayMs());
            context.memory().putInt(operationKey, delayOperation);
            context.memory().putLong(readyAtKey, context.nowMs() + delayMs);
        }
        return context.nowMs() < context.memory().longValue(readyAtKey, context.nowMs());
    }

    AgentCapabilityStep approachReactor(AgentCapabilityContext context,
                                        int mapId,
                                        int questId,
                                        Integer reactorId,
                                        String reactorName) {
        AgentCapabilityStep travel = travel(context, mapId);
        if (travel != null) {
            return travel;
        }
        var request = new AgentReactorInteractionRequest(
                mapId, questId, AgentReactorInteractionMode.HIT,
                reactorId, reactorName, null, gateway.position(context.agent()), -1);
        var target = new AgentReactorTargetSelector()
                .selectReserved(List.copyOf(gateway.reactors(context.agent())), request,
                        context.agent().getId(), context.agent().getMap());
        if (target.isEmpty()) {
            return missing("no matching active reactor is available");
        }
        Point position = target.get().targetPosition();
        if (gateway.position(context.agent()).distanceSq(position)
                <= (long) REACTOR_RANGE_PX * REACTOR_RANGE_PX) {
            return null;
        }
        return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(mapId, position, REACTOR_RANGE_PX, true)),
                "objective requests navigation to reactor");
    }

    AgentCapabilityInvocation<?> talk(int mapId, int npcId, Integer questId) {
        return talk(mapId, npcId, questId, NPC_RANGE_PX);
    }

    AgentCapabilityInvocation<?> talk(int mapId, int npcId, Integer questId, int interactionRangePx) {
        return invocation(new AgentNpcInteractionPrimitiveCapability(gateway, scopePolicy),
                new AgentNpcInteractionPrimitiveCapability.Command(mapId, npcId,
                        AgentNpcInteractionType.TALK, questId, interactionRangePx, true));
    }

    AgentCapabilityInvocation<?> questStart(int questId, int npcId) {
        return invocation(new AgentQuestStartPrimitiveCapability(gateway, scopePolicy),
                new AgentQuestStartPrimitiveCapability.Command(questId, npcId, true));
    }

    AgentCapabilityInvocation<?> questComplete(int questId, int npcId) {
        return invocation(new AgentQuestCompletePrimitiveCapability(gateway, scopePolicy),
                new AgentQuestCompletePrimitiveCapability.Command(questId, npcId, true));
    }

    AgentCapabilityInvocation<?> questState(int questId, int status) {
        return invocation(new AgentQuestStateCapability(gateway),
                new AgentQuestStateCapability.Command(questId, status));
    }

    AgentCapabilityInvocation<?> inspect(int itemId, int count) {
        return invocation(new AgentInventoryInspectionCapability(gateway),
                new AgentInventoryInspectionCapability.Command(itemId, count, 0));
    }

    AgentCapabilityInvocation<?> useItem(int itemId, int initialCount, int questId) {
        return invocation(new AgentItemUseCapability(gateway),
                new AgentItemUseCapability.Command(itemId, initialCount, initialCount - 1, questId, 1));
    }

    AgentCapabilityInvocation<?> combat(int questId, Map<Integer, Integer> kills) {
        return combat(questId, kills, Map.of());
    }

    AgentCapabilityInvocation<?> combat(int questId,
                                        Map<Integer, Integer> kills,
                                        Map<Integer, Integer> loot) {
        return combat(questId, kills, loot, COMBAT_TIMEOUT_MS);
    }

    AgentCapabilityInvocation<?> combat(int questId,
                                        Map<Integer, Integer> kills,
                                        Map<Integer, Integer> loot,
                                        long timeoutMs) {
        return invocation(new AgentCombatCapability(gateway),
                new AgentCombatCapability.Command(questId, kills, loot), timeoutMs);
    }

    AgentCapabilityInvocation<?> reactor(int mapId,
                                         int questId,
                                         Integer reactorId,
                                         String reactorName,
                                         Map<Integer, Integer> items) {
        return invocation(new AgentReactorPrimitiveCapability(gateway,
                        new server.agents.capabilities.reactor.AgentReactorScopePolicy(),
                        new server.agents.capabilities.reactor.AgentReactorTargetSelector()),
                new AgentReactorPrimitiveCapability.Command(
                        mapId, questId, reactorId, reactorName, REACTOR_RANGE_PX, items));
    }

    AgentCapabilityInvocation<?> loot(Map<Integer, Integer> items) {
        return invocation(new AgentLootCapability(gateway),
                new AgentLootCapability.Command(items, AgentLootCapability.ProtectionPolicy.ANY_REQUIRED_ITEM));
    }

    AgentCapabilityInvocation<?> finalState(int mapId,
                                            Map<Integer, Integer> quests,
                                            Map<Integer, Integer> items,
                                            Set<Integer> forbiddenCompletedQuests) {
        return invocation(new AgentFinalStateVerificationCapability(gateway),
                new AgentFinalStateVerificationCapability.Command(mapId, quests, items,
                        true, null, null, forbiddenCompletedQuests));
    }

    static AgentCapabilityResult success(String objectiveId, String message) {
        return AgentCapabilityResult.success(message, new AgentObjectiveResult(objectiveId, message));
    }

    static AgentCapabilityStep missing(String message) {
        return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                AgentCapabilityStatus.MISSING_REQUIREMENT,
                AgentCapabilityReasonCode.MISSING_REQUIREMENT, message));
    }

    static AgentCapabilityStep blocked(AgentCapabilityStatus status, String message) {
        return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                status, AgentCapabilityReasonCode.BLOCKED_BY_SCOPE, message));
    }

    private static <C extends server.agents.capabilities.runtime.AgentCapabilityCommand>
    AgentCapabilityInvocation<C> invocation(server.agents.capabilities.runtime.AgentExecutableCapability<C> capability,
                                            C command) {
        return new AgentCapabilityInvocation<>(capability, command, CHILD_TIMEOUT_MS, CHILD_RETRIES);
    }

    private static <C extends server.agents.capabilities.runtime.AgentCapabilityCommand>
    AgentCapabilityInvocation<C> invocation(server.agents.capabilities.runtime.AgentExecutableCapability<C> capability,
                                            C command,
                                            long timeoutMs) {
        return new AgentCapabilityInvocation<>(capability, command, timeoutMs, CHILD_RETRIES);
    }
}
