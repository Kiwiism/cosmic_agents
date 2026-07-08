package server.agents.runtime;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;

import java.awt.Point;

public final class AgentLiveModeTickRuntime {
    private AgentLiveModeTickRuntime() {
    }

    public record LocalAttackResult(boolean consumedTick, Point targetPos) {
    }

    @FunctionalInterface
    public interface LocalOpportunityAttack {
        LocalAttackResult attack(AgentRuntimeEntry entry,
                                 Character agent,
                                 Point agentPosition,
                                 Point targetPosition,
                                 Point followTargetPosition,
                                 boolean allowMoveWindow,
                                 boolean updateMoveWindow);
    }

    @FunctionalInterface
    public interface MovementCoreStep {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface AnchoredFarmTick {
        void tick(AgentRuntimeEntry entry, Character agent, Point agentPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface GrindModeTick {
        LocalAttackResult tick(AgentRuntimeEntry entry, Character agent, Point agentPosition, Point targetPosition, boolean runAiTick);
    }

    public static AgentLiveModeTickService.Result tickLiveModes(
            AgentLiveModeTickService.Context context,
            boolean perf,
            LocalOpportunityAttack localOpportunityAttack,
            MovementCoreStep movementCoreStep,
            AnchoredFarmTick anchoredFarmTick,
            GrindModeTick grindModeTick,
            int followDistance) {
        return AgentLiveModeTickService.tickLiveModes(
                context,
                hooks(
                        perf,
                        localOpportunityAttack,
                        movementCoreStep,
                        anchoredFarmTick,
                        grindModeTick,
                        followDistance));
    }

    private static AgentLiveModeTickService.Hooks hooks(boolean perf,
                                                        LocalOpportunityAttack localOpportunityAttack,
                                                        MovementCoreStep movementCoreStep,
                                                        AnchoredFarmTick anchoredFarmTick,
                                                        GrindModeTick grindModeTick,
                                                        int followDistance) {
        return new AgentLiveModeTickService.Hooks(
                (shopEntry, shopAgent, shopRunAiTick) -> {
                    AgentShopVisitTickService.Result shopVisitResult = AgentShopVisitTickService.tickShopVisitIfPending(
                            shopEntry,
                            shopAgent,
                            shopRunAiTick,
                            new AgentShopVisitTickService.Hooks(
                                    (visitEntry, visitAgent) -> tickShopVisit(visitEntry, visitAgent, perf),
                                    (moveEntry, moveTargetPos, moveRunAiTick) ->
                                            timedMovementCoreStep(moveEntry, moveTargetPos, moveRunAiTick, perf, movementCoreStep)));
                    return new AgentLiveModeTickService.PhaseResult(
                            shopVisitResult.consumedTick(),
                            shopVisitResult.targetPos());
                },
                (attackEntry, attackAgent, attackAgentPos, attackTargetPos, attackFollowTargetPos, attackFollowAnchor, attackRunAiTick) -> {
                    AgentFollowOpportunityTickService.Result followOpportunity =
                            AgentFollowOpportunityTickService.tickFollowOpportunity(
                                    attackEntry,
                                    attackAgent,
                                    attackAgentPos,
                                    attackTargetPos,
                                    attackFollowTargetPos,
                                    attackFollowAnchor,
                                    attackRunAiTick,
                                    new AgentFollowOpportunityTickService.Hooks(
                                            (localEntry, localAgent, localAgentPos, localTargetPos, localFollowTargetPos) -> {
                                                LocalAttackResult result = timedLocalOpportunityAttack(
                                                        localEntry,
                                                        localAgent,
                                                        localAgentPos,
                                                        localTargetPos,
                                                        localFollowTargetPos,
                                                        perf,
                                                        localOpportunityAttack);
                                                return new AgentFollowOpportunityTickService.Result(
                                                        result.consumedTick(),
                                                        result.targetPos());
                                            },
                                            followDistance));
                    return new AgentLiveModeTickService.PhaseResult(
                            followOpportunity.consumedTick(),
                            followOpportunity.targetPos());
                },
                (idleEntry, idleAgent, idleTargetPos, idleNowMs) ->
                        AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(
                                idleEntry, idleAgent, idleTargetPos, idleNowMs),
                (scriptEntry, scriptAgent, scriptAgentPos, scriptTargetPos, scriptRunAiTick) -> {
                    AgentScriptedMoveCombatTickService.Result scriptedMoveCombat =
                            AgentScriptedMoveCombatTickService.tickScriptedMoveCombat(
                                    scriptEntry,
                                    scriptAgent,
                                    scriptAgentPos,
                                    scriptTargetPos,
                                    scriptRunAiTick,
                                    new AgentScriptedMoveCombatTickService.Hooks(
                                            (entry, agentPosition, targetPosition) ->
                                                    AgentLocalAttackMoveWindowRuntime.clearActionMoveWindowIfSettled(
                                                            entry, agentPosition, targetPosition),
                                            (attackEntry, attackAgent, attackAgentPos, attackTargetPos) -> {
                                                LocalAttackResult result = timedScriptedOpportunityAttack(
                                                        attackEntry,
                                                        attackAgent,
                                                        attackAgentPos,
                                                        attackTargetPos,
                                                        perf,
                                                        localOpportunityAttack);
                                                return new AgentScriptedMoveCombatTickService.Result(
                                                        result.consumedTick(),
                                                        result.targetPos());
                                            },
                                            (moveEntry, moveTargetPos, moveRunAiTick) ->
                                                    timedMovementCoreStep(moveEntry, moveTargetPos, moveRunAiTick, perf, movementCoreStep)));
                    return new AgentLiveModeTickService.PhaseResult(
                            scriptedMoveCombat.consumedTick(),
                            scriptedMoveCombat.targetPos());
                },
                (farmEntry, farmAgent, farmAgentPos, farmRunAiTick) -> AgentAnchoredFarmModeTickService.tickIfAnchoredFarm(
                        farmEntry,
                        farmAgent,
                        farmAgentPos,
                        farmRunAiTick,
                        new AgentAnchoredFarmModeTickService.Hooks((anchoredEntry, anchoredAgent, anchoredAgentPos, anchoredRunAiTick) ->
                                timedAnchoredFarmTick(
                                        anchoredEntry,
                                        anchoredAgent,
                                        anchoredAgentPos,
                                        anchoredRunAiTick,
                                        perf,
                                        anchoredFarmTick))),
                (grindEntry, grindAgent, grindAgentPos, grindTargetPos, grindRunAiTick) -> {
                    AgentGrindModeDispatchService.Result grindDispatch = AgentGrindModeDispatchService.tickIfGrinding(
                            grindEntry,
                            grindAgent,
                            grindAgentPos,
                            grindTargetPos,
                            grindRunAiTick,
                            new AgentGrindModeDispatchService.Hooks((dispatchEntry, dispatchAgent, dispatchAgentPos, dispatchTargetPos, dispatchRunAiTick) -> {
                                LocalAttackResult grindResult = timedGrindModeTick(
                                        dispatchEntry,
                                        dispatchAgent,
                                        dispatchAgentPos,
                                        dispatchTargetPos,
                                        dispatchRunAiTick,
                                        perf,
                                        grindModeTick);
                                return new AgentGrindModeDispatchService.Result(
                                        grindResult.consumedTick(),
                                        grindResult.targetPos());
                            }));
                    return new AgentLiveModeTickService.PhaseResult(
                            grindDispatch.consumedTick(),
                            grindDispatch.targetPos());
                },
                (moveEntry, moveTargetPos, moveRunAiTick) -> AgentFinalMovementTailService.stepFinalMovement(
                        moveEntry,
                        moveTargetPos,
                        moveRunAiTick,
                        new AgentFinalMovementTailService.Hooks((ignored, tailTargetPos, tailRunAiTick) ->
                                timedMovementCoreStep(moveEntry, tailTargetPos, tailRunAiTick, perf, movementCoreStep))));
    }

    private static boolean tickShopVisit(AgentRuntimeEntry entry, Character agent, boolean perf) {
        if (!perf) {
            return AgentShopService.tickShopVisit(entry, agent);
        }
        long startedAt = System.nanoTime();
        boolean consumed = AgentShopService.tickShopVisit(entry, agent);
        AgentPerformanceMonitor.record("tick-shop-visit", System.nanoTime() - startedAt);
        return consumed;
    }

    private static LocalAttackResult timedLocalOpportunityAttack(AgentRuntimeEntry entry,
                                                                 Character agent,
                                                                 Point agentPosition,
                                                                 Point targetPosition,
                                                                 Point followTargetPosition,
                                                                 boolean perf,
                                                                 LocalOpportunityAttack localOpportunityAttack) {
        if (!perf) {
            return localOpportunityAttack.attack(
                    entry, agent, agentPosition, targetPosition, followTargetPosition, true, true);
        }
        long startedAt = System.nanoTime();
        try {
            return localOpportunityAttack.attack(
                    entry, agent, agentPosition, targetPosition, followTargetPosition, true, true);
        } finally {
            AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - startedAt);
        }
    }

    private static LocalAttackResult timedScriptedOpportunityAttack(AgentRuntimeEntry entry,
                                                                    Character agent,
                                                                    Point agentPosition,
                                                                    Point targetPosition,
                                                                    boolean perf,
                                                                    LocalOpportunityAttack localOpportunityAttack) {
        if (!perf) {
            return localOpportunityAttack.attack(
                    entry, agent, agentPosition, targetPosition, targetPosition, true, true);
        }
        long startedAt = System.nanoTime();
        try {
            return localOpportunityAttack.attack(
                    entry, agent, agentPosition, targetPosition, targetPosition, true, true);
        } finally {
            AgentPerformanceMonitor.record("opportunity-attack", System.nanoTime() - startedAt);
        }
    }

    private static void timedAnchoredFarmTick(AgentRuntimeEntry entry,
                                              Character agent,
                                              Point agentPosition,
                                              boolean runAiTick,
                                              boolean perf,
                                              AnchoredFarmTick anchoredFarmTick) {
        if (!perf) {
            anchoredFarmTick.tick(entry, agent, agentPosition, runAiTick);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            anchoredFarmTick.tick(entry, agent, agentPosition, runAiTick);
        } finally {
            AgentPerformanceMonitor.record("tick-anchored-farm", System.nanoTime() - startedAt);
        }
    }

    private static LocalAttackResult timedGrindModeTick(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        Point agentPosition,
                                                        Point targetPosition,
                                                        boolean runAiTick,
                                                        boolean perf,
                                                        GrindModeTick grindModeTick) {
        if (!perf) {
            return grindModeTick.tick(entry, agent, agentPosition, targetPosition, runAiTick);
        }
        long startedAt = System.nanoTime();
        try {
            return grindModeTick.tick(entry, agent, agentPosition, targetPosition, runAiTick);
        } finally {
            AgentPerformanceMonitor.record("tick-grind-dispatch", System.nanoTime() - startedAt);
        }
    }

    private static void timedMovementCoreStep(AgentRuntimeEntry entry,
                                              Point targetPosition,
                                              boolean runAiTick,
                                              boolean perf,
                                              MovementCoreStep movementCoreStep) {
        if (!perf) {
            movementCoreStep.step(entry, targetPosition, runAiTick);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            movementCoreStep.step(entry, targetPosition, runAiTick);
        } finally {
            AgentPerformanceMonitor.record("step-movement-core", System.nanoTime() - startedAt);
        }
    }

}
