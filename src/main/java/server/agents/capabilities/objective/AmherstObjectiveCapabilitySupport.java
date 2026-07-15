package server.agents.capabilities.objective;

import server.agents.capabilities.AgentCapabilityStatus;
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
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;
import java.util.Map;
import java.util.Set;

final class AmherstObjectiveCapabilitySupport {
    static final long CHILD_TIMEOUT_MS = 30_000L;
    static final long COMBAT_TIMEOUT_MS = 180_000L;
    static final long PORTAL_TIMEOUT_MS = 90_000L;
    static final int CHILD_RETRIES = 2;
    static final int NPC_RANGE_PX = AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX;
    static final int REACTOR_RANGE_PX = 60;

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstScopePolicy scopePolicy;
    private final AmherstNpcInteractionDelay npcInteractionDelay;

    AmherstObjectiveCapabilitySupport() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy(),
                AmherstNpcInteractionDelay.NONE);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway) {
        this(gateway, new AmherstScopePolicy(), AmherstNpcInteractionDelay.NONE);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway, AmherstScopePolicy scopePolicy) {
        this(gateway, scopePolicy, AmherstNpcInteractionDelay.NONE);
    }

    AmherstObjectiveCapabilitySupport(PrimitiveCapabilityGateway gateway,
                                      AmherstScopePolicy scopePolicy,
                                      AmherstNpcInteractionDelay npcInteractionDelay) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
        this.npcInteractionDelay = npcInteractionDelay == null
                ? AmherstNpcInteractionDelay.NONE : npcInteractionDelay;
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
                new AgentPortalTravelCapability.Command(sourceMapId, portalId, destinationMapId, true),
                PORTAL_TIMEOUT_MS),
                "objective requests portal travel");
    }

    AgentCapabilityStep approachNpc(AgentCapabilityContext context, int mapId, int npcId) {
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
        if (gateway.position(context.agent()).distanceSq(npcPosition) <= (long) NPC_RANGE_PX * NPC_RANGE_PX) {
            if (!gateway.grounded(context.agent())) {
                return AgentCapabilityStep.running("waiting to land before NPC interaction", false);
            }
            gateway.facePosition(context.agent(), npcPosition);
            return null;
        }
        return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(mapId, npcPosition, NPC_RANGE_PX, true)),
                "objective requests navigation to NPC");
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
        String operationKey = "npcDelayOperation";
        String readyAtKey = "npcReadyAtMs";
        if (context.memory().intValue(operationKey, -1) != operationIndex) {
            long delayMs = Math.max(0L, npcInteractionDelay.nextDelayMs());
            context.memory().putInt(operationKey, operationIndex);
            context.memory().putLong(readyAtKey, context.nowMs() + delayMs);
        }
        return context.nowMs() < context.memory().longValue(readyAtKey, context.nowMs());
    }

    AgentCapabilityStep approachReactor(AgentCapabilityContext context,
                                        int mapId,
                                        Integer reactorId,
                                        String reactorName) {
        AgentCapabilityStep travel = travel(context, mapId);
        if (travel != null) {
            return travel;
        }
        Point position = gateway.nearestActiveReactorPosition(context.agent(), reactorId, reactorName);
        if (position == null) {
            return missing("no matching active reactor is available");
        }
        if (gateway.position(context.agent()).distanceSq(position)
                <= (long) REACTOR_RANGE_PX * REACTOR_RANGE_PX) {
            return null;
        }
        return AgentCapabilityStep.handoff(invocation(new AgentNavigationCapability(gateway),
                new AgentNavigationCapability.Command(mapId, position, REACTOR_RANGE_PX, true)),
                "objective requests navigation to reactor");
    }

    AgentCapabilityInvocation<?> talk(int mapId, int npcId, Integer questId) {
        return invocation(new AgentNpcInteractionPrimitiveCapability(gateway, scopePolicy),
                new AgentNpcInteractionPrimitiveCapability.Command(mapId, npcId,
                        AgentNpcInteractionType.TALK, questId, true));
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
        return invocation(new AgentCombatCapability(gateway),
                new AgentCombatCapability.Command(questId, kills, loot), COMBAT_TIMEOUT_MS);
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
