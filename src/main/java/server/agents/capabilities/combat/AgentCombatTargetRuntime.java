package server.agents.capabilities.combat;

import server.agents.capabilities.supplies.AgentAmmoStateRuntime;

import client.Character;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.perception.AgentMapPerception;
import server.agents.perception.AgentPeerPerception;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.capabilities.looting.AgentPreExitLootRuntime;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;
import client.inventory.WeaponType;

import java.awt.Point;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class AgentCombatTargetRuntime {
    private static final long UNREACHABLE_GRAPH_COST = Long.MAX_VALUE / 4;

    private AgentCombatTargetRuntime() {
    }

    public static Monster findGrindTarget(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            long nowMs = System.currentTimeMillis();
            synchronizeSearchMode(entry, bot, nowMs);
            if (AgentPreExitLootRuntime.active(entry, nowMs)) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.GRIND,
                        AgentCombatDecisionTraceState.Outcome.PRE_EXIT_LOOT,
                        0, 0, 0, 0, 0, false, false, null);
                return null;
            }
            AgentCombatDecisionContext decisionContext =
                    AgentCombatDecisionContext.capture(entry, bot, nowMs);
            Point botPos = decisionContext.startPos();
            double rangeSq = (double) config.GRIND_SEEK_RANGE * config.GRIND_SEEK_RANGE;
            Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatCandidateProvider.local(bot, botPos, rangeSq);
            int baseCandidateCount = candidates.size();
            candidates.removeIf(monster -> !AgentCombatObjectiveTargetStateRuntime.allows(entry, monster.getId()));
            int objectiveCandidateCount = candidates.size();
            AgentCombatDecisionContext assignmentContext = decisionContext;
            AgentCombatRegionAssignmentPolicy.SearchScope<Monster> assignmentScope =
                    AgentCombatRegionAssignmentPolicy.begin(
                            entry, AgentCombatDirectiveRuntime.directive(entry), bot.getMapId(),
                            candidates,
                            monster -> assignmentContext.available()
                                    ? AgentNavigationRegionService.resolveTargetRegionId(
                                    assignmentContext.graph(), assignmentContext.entry(),
                                    assignmentContext.map(), monster.getPosition())
                                    : -1,
                            monster -> monster.getPosition().x,
                            nowMs);
            candidates = new ArrayList<>(assignmentScope.localCandidates());
            int localPreferredCandidateCount = (int) candidates.stream()
                    .filter(monster -> AgentCombatObjectiveTargetStateRuntime.prefers(entry, monster.getId()))
                    .count();
            PlatformBatchSelection platformBatch = retainPlatformBatchCandidates(
                    entry, bot, candidates, nowMs, decisionContext);
            candidates = platformBatch.candidates();
            TargetPromotion promotion = platformBatch.retained()
                    ? new TargetPromotion(candidates, false)
                    : promoteMapWidePreferredTargetsResult(
                    entry, bot, candidates, nowMs,
                    target -> hasCompleteRemoteCombatRoute(entry, bot, target, decisionContext));
            candidates = promotion.candidates();
            candidates = assignmentScope.apply(candidates,
                    monster -> assignmentContext.available()
                            ? AgentNavigationRegionService.resolveTargetRegionId(
                            assignmentContext.graph(), assignmentContext.entry(),
                            assignmentContext.map(), monster.getPosition())
                            : -1,
                    monster -> monster.getPosition().x);
            boolean mapWidePreferredEscalation = promotion.mapWide();
            if (mapWidePreferredEscalation) {
                objectiveCandidateCount = candidates.size();
            }
            if (candidates.isEmpty()) {
                AgentCombatBehaviorRuntime.noCandidateOpportunity(entry);
                recordSearchRanking(entry, bot, List.of(),
                        baseCandidateCount, localPreferredCandidateCount, decisionContext);
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.GRIND,
                        baseCandidateCount > 0
                                ? AgentCombatDecisionTraceState.Outcome.OBJECTIVE_FILTERED
                                : AgentCombatDecisionTraceState.Outcome.NO_CANDIDATES,
                        baseCandidateCount, objectiveCandidateCount, 0, 0, 0,
                        mapWidePreferredEscalation, false, null);
                return null;
            }
            PolicySelection policySelection = applyObjectivePolicy(
                    entry, bot, botPos, botFoothold, candidates,
                    platformBatch.retained(), decisionContext);
            candidates = policySelection.candidates();
            int policyCandidateCount = candidates.size();

            Map<Monster, Integer> targetOccupancy =
                    grindTargetOccupancy(entry, bot, decisionContext);
            candidates = territorialFieldAssignment(entry, bot, nowMs)
                    ? AgentCombatBehaviorRuntime.respectFieldClaims(candidates, targetOccupancy)
                    : AgentCombatBehaviorRuntime.respectClaims(entry, candidates, targetOccupancy);
            int claimCandidateCount = candidates.size();
            // A platform batch is already an explicit combat commitment. Re-applying the
            // personality response delay after each kill created a visible pause between
            // otherwise legal safe-spot shots and undermined the bounded local clear policy.
            if (!platformBatch.retained() && !AgentCombatBehaviorRuntime.responseReady(
                    entry, bot, candidates, nowMs)) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.GRIND,
                        claimCandidateCount == 0
                                ? (policyCandidateCount == 0
                                ? AgentCombatDecisionTraceState.Outcome.POLICY_FILTERED
                                : AgentCombatDecisionTraceState.Outcome.CLAIMS_FILTERED)
                                : AgentCombatDecisionTraceState.Outcome.RESPONSE_DEFERRED,
                        baseCandidateCount, objectiveCandidateCount, policyCandidateCount,
                        claimCandidateCount, 0, mapWidePreferredEscalation, false, null);
                return null;
            }
            List<AgentScoredGrindTarget> scoredTargets = scoreGrindTargets(
                    entry, bot, botPos, botFoothold, candidates, targetOccupancy, config,
                    mapWidePreferredEscalation, decisionContext);
            recordSearchRanking(entry, bot, scoredTargets,
                    baseCandidateCount, localPreferredCandidateCount, decisionContext);
            if (scoredTargets.isEmpty()) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.GRIND,
                        claimCandidateCount == 0
                                ? AgentCombatDecisionTraceState.Outcome.CLAIMS_FILTERED
                                : AgentCombatDecisionTraceState.Outcome.UNREACHABLE,
                        baseCandidateCount, objectiveCandidateCount, policyCandidateCount,
                        claimCandidateCount, 0, mapWidePreferredEscalation, false, null);
                return null;
            }

            RankedTargetSelection rankedSelection =
                    selectVariedReachableTarget(entry, bot, scoredTargets);
            Monster selected = rankedSelection.target();
            if (!rankedSelection.variationDecisionConsumed()) {
                selected = selectVariedTargetWithinWinningRegion(
                        entry, bot, botPos, botFoothold, candidates,
                        targetOccupancy, config, selected, decisionContext);
            }
            AgentCombatVariationRuntime.maybeAnchorAtTarget(
                    entry, bot, selected, targetRegionId(decisionContext, selected));
            if (mapWidePreferredEscalation && selected != null) {
                int selectedRegionId = targetRegionId(decisionContext, selected);
                AgentCombatLocalTargetLeaseRuntime.beganMapWideTravel(
                        entry, bot, selectedRegionId);
                AgentCombatTargetSearchModeState searchMode = searchModeState(entry, bot, nowMs);
                if (searchMode != null
                        && AgentCombatObjectiveTargetStateRuntime.prefers(entry, selected.getId())) {
                    searchMode.enter(AgentCombatTargetSearchMode.MAP_WIDE_RECOVERY,
                            "travelling to a reachable required population",
                            selectedRegionId, nowMs);
                }
            }
            if (!mapWidePreferredEscalation && selected != null) {
                beginPlatformBatch(entry, bot, selected, nowMs, decisionContext);
            }
            recordPolicySelection(entry, bot, selected, policySelection);
            AgentCombatBehaviorRuntime.targetAcquired(entry);
            recordDecision(entry, AgentCombatDecisionTraceState.Mode.GRIND,
                    AgentCombatDecisionTraceState.Outcome.SELECTED,
                    baseCandidateCount, objectiveCandidateCount, policyCandidateCount,
                    claimCandidateCount, scoredTargets.size(), mapWidePreferredEscalation,
                    rankedSelection.variationDecisionConsumed(), selected);
            return selected;
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    public static Monster findPatrolTarget(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            if (entry == null || bot == null || !AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
                return null;
            }
            if (AgentPreExitLootRuntime.active(entry, System.currentTimeMillis())) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                        AgentCombatDecisionTraceState.Outcome.PRE_EXIT_LOOT,
                        0, 0, 0, 0, 0, false, false, null);
                return null;
            }
            AgentCombatDecisionContext decisionContext =
                    AgentCombatDecisionContext.capture(entry, bot, System.currentTimeMillis());
            Point botPos = decisionContext.startPos();
            double rangeSq = (double) config.GRIND_SEEK_RANGE * config.GRIND_SEEK_RANGE;
            Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatCandidateProvider.local(bot, botPos, rangeSq);
            int baseCandidateCount = candidates.size();
            candidates.removeIf(monster -> !AgentCombatObjectiveTargetStateRuntime.allows(entry, monster.getId()));
            int objectiveCandidateCount = candidates.size();
            if (shouldEscalateToMapWidePreferredTarget(entry, bot, candidates)) {
                AgentCombatVariationRuntime.clearAutomaticAnchor(entry);
                return findGrindTarget(entry, bot, config);
            }
            if (candidates.isEmpty()) {
                AgentCombatBehaviorRuntime.noCandidateOpportunity(entry);
                releaseEmptyAutomaticAnchor(entry);
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                        baseCandidateCount > 0
                                ? AgentCombatDecisionTraceState.Outcome.OBJECTIVE_FILTERED
                                : AgentCombatDecisionTraceState.Outcome.NO_CANDIDATES,
                        baseCandidateCount, objectiveCandidateCount, 0, 0, 0,
                        false, false, null);
                return null;
            }
            GrindGraphContext graphContext = GrindGraphContext.from(decisionContext);
            if (!graphContext.available()) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                        AgentCombatDecisionTraceState.Outcome.GRAPH_UNAVAILABLE,
                        baseCandidateCount, objectiveCandidateCount, 0, 0, 0,
                        false, false, null);
                return null;
            }
            AgentNavigationGraph graph = graphContext.graph();
            MapleMap map = graphContext.map();
            int patrolId = AgentPatrolStateRuntime.patrolRegionId(entry);
            Set<Integer> adjacentIds = graph.getMutualAdjacentRegionIds(patrolId);

            List<Monster> filtered = new ArrayList<>();
            for (Monster m : candidates) {
                if (graph.findRegionId(map, m.getPosition()) == patrolId) {
                    filtered.add(m);
                }
            }
            if (filtered.isEmpty()) {
                if (AgentCombatVariationRuntime.isAutomaticPlatformAnchor(entry)) {
                    AgentCombatVariationRuntime.clearAutomaticAnchor(entry);
                    return findGrindTarget(entry, bot, config);
                }
                for (Monster m : candidates) {
                    int mId = graph.findRegionId(map, m.getPosition());
                    if (mId == patrolId || adjacentIds.contains(mId)) {
                        filtered.add(m);
                    }
                }
            }
            if (filtered.isEmpty()) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                        AgentCombatDecisionTraceState.Outcome.POLICY_FILTERED,
                        baseCandidateCount, objectiveCandidateCount, 0, 0, 0,
                        false, false, null);
                return null;
            }
            PolicySelection policySelection = applyObjectivePolicy(
                    entry, bot, botPos, botFoothold, filtered, false, decisionContext);
            filtered = policySelection.candidates();
            int policyCandidateCount = filtered.size();

            List<AgentScoredGrindTarget> scored = scoreGrindTargets(
                    entry,
                    bot,
                    botPos,
                    botFoothold,
                    filtered,
                    grindTargetOccupancy(entry, bot, decisionContext),
                    config,
                    false,
                    decisionContext);
            if (scored.isEmpty()) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                        policyCandidateCount == 0
                                ? AgentCombatDecisionTraceState.Outcome.POLICY_FILTERED
                                : AgentCombatDecisionTraceState.Outcome.UNREACHABLE,
                        baseCandidateCount, objectiveCandidateCount, policyCandidateCount,
                        policyCandidateCount, 0, false, false, null);
                return null;
            }
            Monster selected = AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(
                    scored, UNREACHABLE_GRAPH_COST);
            recordPolicySelection(entry, bot, selected, policySelection);
            recordDecision(entry, AgentCombatDecisionTraceState.Mode.PATROL,
                    AgentCombatDecisionTraceState.Outcome.SELECTED,
                    baseCandidateCount, objectiveCandidateCount, policyCandidateCount,
                    policyCandidateCount, scored.size(), false, false, selected);
            return selected;
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    public static Monster findFollowAttackTarget(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double range = Math.max(
                    AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                            + AgentProjectileHitbox.passiveProjectileRangeBonus(bot),
                    config.ATTACK_RANGE_X + config.ATTACK_JUMP_X_EXTRA);
            List<Monster> candidates = AgentCombatCandidateProvider.local(bot, botPos, range * range);
            if (candidates.isEmpty()) {
                recordDecision(entry, AgentCombatDecisionTraceState.Mode.FOLLOW,
                        AgentCombatDecisionTraceState.Outcome.NO_CANDIDATES,
                        0, 0, 0, 0, 0, false, false, null);
                return null;
            }

            Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
            GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
            List<AgentScoredGrindTarget> localTargets = AgentCombatGrindTargetPolicy.scoreFollowLocalTargets(
                    candidates,
                    botPos,
                    candidate -> isLocalCombatTarget(graphContext, bot, botFoothold, candidate)
                            || AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(
                            bot,
                            candidate,
                            entry == null || AgentAmmoStateRuntime.noAmmo(entry),
                            entry == null ? 0 : AgentCombatSkillCacheStateRuntime.attackSkillId(entry)),
                    candidate -> grindTargetScore(
                            bot, botPos, botFoothold, candidate, Map.of(), config),
                    candidate -> AgentCombatScoringPolicy.aoeClusterBonus(
                            candidate,
                            candidates,
                            entry != null && AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                            entry == null ? 0 : AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
            Monster selected = AgentCombatGrindTargetPolicy.pickFromBestTargets(localTargets);
            recordDecision(entry, AgentCombatDecisionTraceState.Mode.FOLLOW,
                    selected == null
                            ? AgentCombatDecisionTraceState.Outcome.UNREACHABLE
                            : AgentCombatDecisionTraceState.Outcome.SELECTED,
                    candidates.size(), candidates.size(), candidates.size(), candidates.size(),
                    localTargets.size(), false, false, selected);
            return selected;
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    public static boolean isReachableGrindTarget(AgentRuntimeEntry entry, Character bot, Monster target) {
        boolean targetPresentAndAlive = target != null && target.isAlive();
        boolean hasRuntimeContext = entry != null && bot != null;
        GrindGraphContext graphContext = targetPresentAndAlive && hasRuntimeContext
                ? GrindGraphContext.resolve(entry, bot, bot.getPosition())
                : null;
        boolean immediateProjectileTarget = targetPresentAndAlive && hasRuntimeContext
                && AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(
                bot,
                target,
                entry == null || AgentAmmoStateRuntime.noAmmo(entry),
                entry == null ? 0 : AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        boolean graphAvailable = graphContext != null && graphContext.available();
        long targetCost = UNREACHABLE_GRAPH_COST;
        if (targetPresentAndAlive && hasRuntimeContext && !immediateProjectileTarget && graphAvailable) {
            Point targetPos = target.getPosition();
            int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                    graphContext.graph(), graphContext.entry(), graphContext.map(), targetPos);
            if (targetRegionId >= 0) {
                targetCost = AgentNavigationPathService.reliableRouteCost(
                        graphContext.graph(),
                        graphContext.map(),
                        graphContext.startPos(),
                        graphContext.startRegionId(),
                        targetPos,
                        targetRegionId,
                        AgentCombatScoringPolicy.estimateLocalTravelCostMs(
                                graphContext.startPos(), targetPos,
                                graphContext.profile().walkVelocityPxs()),
                        entry,
                        bot,
                        UNREACHABLE_GRAPH_COST);
            }
        }
        return AgentCombatGrindTargetPolicy.isReachableGrindTarget(
                targetPresentAndAlive,
                hasRuntimeContext,
                immediateProjectileTarget,
                graphAvailable,
                targetCost,
                UNREACHABLE_GRAPH_COST);
    }

    /**
     * Returns true when a required target in the local route neighborhood should interrupt the
     * current commitment. Equal-locality remote candidates never preempt one another here.
     */
    public static boolean hasBetterLocalPreferredOpportunity(AgentRuntimeEntry entry,
                                                              Character bot,
                                                              Monster currentTarget) {
        if (entry == null || bot == null || bot.getMap() == null || bot.getPosition() == null
                || currentTarget == null) {
            return false;
        }
        Point botPos = bot.getPosition();
        double rangeSq = (double) AgentCombatConfig.cfg.GRIND_SEEK_RANGE
                * AgentCombatConfig.cfg.GRIND_SEEK_RANGE;
        List<Monster> preferred = AgentCombatCandidateProvider.local(
                bot, botPos, rangeSq).stream()
                .filter(monster -> monster != currentTarget)
                .filter(monster -> AgentCombatObjectiveTargetStateRuntime.allows(entry, monster.getId()))
                .filter(monster -> AgentCombatObjectiveTargetStateRuntime.prefers(entry, monster.getId()))
                .toList();
        if (preferred.isEmpty()) {
            return false;
        }

        GrindGraphContext context = GrindGraphContext.resolve(entry, bot, botPos);
        Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
        int currentLocality = targetLocalityClass(entry, context, bot, botFoothold, currentTarget);
        boolean currentPreferred = AgentCombatObjectiveTargetStateRuntime.prefers(
                entry, currentTarget.getId());
        for (Monster candidate : preferred) {
            int candidateLocality = targetLocalityClass(entry, context, bot, botFoothold, candidate);
            if (AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(
                    currentPreferred, currentLocality, candidateLocality)) {
                return true;
            }
        }
        return false;
    }

    private static int targetLocalityClass(AgentRuntimeEntry entry,
                                           GrindGraphContext context,
                                           Character bot,
                                           Foothold botFoothold,
                                           Monster target) {
        if (target == null || target.getPosition() == null) {
            return 2;
        }
        if (isLocalCombatTarget(context, bot, botFoothold, target)) {
            return 0;
        }
        long routeCost = reliableRouteCost(context, entry, bot, target);
        return routeCost <= AgentCombatPolicyConfig.localOpportunityRouteCostMs() ? 1 : 2;
    }

    private static long reliableRouteCost(GrindGraphContext context,
                                          AgentRuntimeEntry entry,
                                          Character bot,
                                          Monster target) {
        if (context == null || !context.available() || target == null
                || target.getPosition() == null) {
            return UNREACHABLE_GRAPH_COST;
        }
        int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                context.graph(), context.entry(), context.map(), target.getPosition());
        if (targetRegionId < 0) {
            return UNREACHABLE_GRAPH_COST;
        }
        return reliableRouteCost(context, entry, bot, target.getPosition(), targetRegionId);
    }

    private static long reliableRouteCost(GrindGraphContext context,
                                          AgentRuntimeEntry entry,
                                          Character bot,
                                          Point targetPosition,
                                          int targetRegionId) {
        if (context == null || !context.available() || targetPosition == null || targetRegionId < 0) {
            return UNREACHABLE_GRAPH_COST;
        }
        if (targetRegionId == context.startRegionId()) {
            return AgentCombatScoringPolicy.estimateLocalTravelCostMs(
                    context.startPos(), targetPosition, context.profile().walkVelocityPxs());
        }
        // Map-wide filtering can inspect many monsters on one platform and then score that same
        // platform again. Executability is a region property for this immutable decision frame;
        // keep individual monster distance in localScore and perform the expensive A* once.
        return context.routeCosts().computeIfAbsent(targetRegionId, ignored ->
                AgentNavigationPathService.reliableRouteCost(
                        context.graph(), context.map(), context.startPos(), context.startRegionId(),
                        targetPosition, targetRegionId,
                        AgentCombatScoringPolicy.estimateLocalTravelCostMs(
                                context.startPos(), targetPosition, context.profile().walkVelocityPxs()),
                        entry, bot, UNREACHABLE_GRAPH_COST));
    }

    private static List<AgentScoredGrindTarget> scoreGrindTargets(AgentRuntimeEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Foothold botFoothold,
                                                                  List<Monster> candidates,
                                                                  Map<Monster, Integer> targetOccupancy,
                                                                  AgentCombatConfig.Config config,
                                                                  boolean requiresCompleteRemoteRoute,
                                                                  AgentCombatDecisionContext decisionContext) {
        GrindGraphContext graphContext = GrindGraphContext.from(decisionContext);
        if (requiresCompleteRemoteRoute && !graphContext.available()) {
            return List.of();
        }
        return AgentCombatGrindTargetPolicy.scoreGrindTargets(
                graphContext.available(),
                () -> scoreLocalTargets(entry, bot, botPos, botFoothold, candidates, targetOccupancy, config),
                () -> scoreTargetRegions(entry, graphContext, bot, botPos, botFoothold,
                        candidates, targetOccupancy, config));
    }

    static List<Monster> promoteMapWidePreferredTargets(
            AgentRuntimeEntry entry,
            Character bot,
            List<Monster> localCandidates,
            long nowMs) {
        return promoteMapWidePreferredTargetsResult(entry, bot, localCandidates, nowMs,
                target -> hasCompleteRemoteCombatRoute(entry, bot, target)).candidates();
    }

    static List<Monster> promoteMapWidePreferredTargets(
            AgentRuntimeEntry entry,
            Character bot,
            List<Monster> localCandidates,
            long nowMs,
            Predicate<Monster> remoteRouteAccepted) {
        return promoteMapWidePreferredTargetsResult(
                entry, bot, localCandidates, nowMs, remoteRouteAccepted).candidates();
    }

    private static TargetPromotion promoteMapWidePreferredTargetsResult(
            AgentRuntimeEntry entry,
            Character bot,
            List<Monster> localCandidates,
            long nowMs,
            Predicate<Monster> remoteRouteAccepted) {
        AgentCombatTargetSearchModeState searchMode = searchModeState(entry, bot, nowMs);
        boolean hasLocalPreferred = localCandidates.stream().anyMatch(monster ->
                AgentCombatObjectiveTargetStateRuntime.prefers(entry, monster.getId()));
        boolean localPreferredExhausted = searchMode == null || searchMode.observeLocalPreferred(
                hasLocalPreferred, AgentCombatPolicyConfig.mapWideRecoveryEmptyScans(), nowMs);
        boolean travelWasActive = entry != null && entry.capabilityStates()
                .find(AgentCombatDecisionState.STATE_KEY)
                .map(state -> state.localTargetLease().snapshot(nowMs).phase()
                        == AgentCombatLocalTargetLeaseState.Phase.TRAVELLING)
                .orElse(false);
        boolean mapWideAllowed = AgentCombatLocalTargetLeaseRuntime.allowsMapWidePromotion(
                entry, bot, hasLocalPreferred, nowMs);
        boolean invalidTravelTargetReleased = travelWasActive && mapWideAllowed;
        if (!mapWideAllowed) {
            Monster retainedTravelTarget =
                    AgentCombatLocalTargetLeaseRuntime.retainedTravelTarget(entry, bot, nowMs);
            if (retainedTravelTarget == null) {
                return new TargetPromotion(localCandidates, false);
            }
            if (remoteRouteAccepted.test(retainedTravelTarget)) {
                return new TargetPromotion(List.of(retainedTravelTarget), true);
            }
            AgentCombatLocalTargetLeaseRuntime.cancelTravel(entry);
            invalidTravelTargetReleased = true;
        }
        boolean preferredEscalation = shouldEscalateToMapWidePreferredTarget(
                entry, bot, localCandidates)
                && (localPreferredExhausted || invalidTravelTargetReleased);
        boolean spawnPressureEscalation = localCandidates.isEmpty()
                && allowsMapWideSpawnPressure(entry, bot, nowMs);
        if (!preferredEscalation && !spawnPressureEscalation) {
            return new TargetPromotion(localCandidates, false);
        }
        List<Monster> mapWidePreferred = mapWidePreferredTargets(entry, bot);
        mapWidePreferred.removeIf(target -> !remoteRouteAccepted.test(target));
        List<Monster> mapWideTargets = mapWidePreferred;
        if (mapWideTargets.isEmpty()
                && localCandidates.isEmpty()
                && allowsMapWideSpawnPressure(entry, bot, nowMs)) {
            mapWideTargets = new ArrayList<>(boundedMapWideSpawnPressureTargets(entry, bot));
            mapWideTargets.removeIf(target -> !remoteRouteAccepted.test(target));
        }
        if (mapWideTargets.isEmpty()) {
            return new TargetPromotion(localCandidates, false);
        }
        // A local platform lease must not pin an Agent below the only remaining
        // objective species or prevent bounded remote spawn-pressure cleanup.
        AgentCombatVariationRuntime.clearAutomaticAnchor(entry);
        if (searchMode != null) {
            boolean requiredRecovery = mapWideTargets.stream().anyMatch(monster ->
                    AgentCombatObjectiveTargetStateRuntime.prefers(entry, monster.getId()));
            searchMode.enter(requiredRecovery
                            ? AgentCombatTargetSearchMode.MAP_WIDE_RECOVERY
                            : AgentCombatTargetSearchMode.SPAWN_PRESSURE,
                    requiredRecovery
                            ? "local required population exhausted"
                            : "no required population available; bounded spawn-pressure expansion",
                    -1, nowMs);
        }
        return new TargetPromotion(mapWideTargets, true);
    }

    private static boolean hasCompleteRemoteCombatRoute(AgentRuntimeEntry entry,
                                                        Character bot,
                                                        Monster target) {
        if (entry == null || bot == null || target == null || target.getPosition() == null) {
            return false;
        }
        GrindGraphContext context = GrindGraphContext.resolve(entry, bot, bot.getPosition());
        if (!context.available()) {
            return false;
        }
        int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                context.graph(), entry, context.map(), target.getPosition());
        if (targetRegionId < 0) {
            return false;
        }
        return targetRegionId == context.startRegionId()
                || reliableRouteCost(context, entry, bot, target.getPosition(), targetRegionId)
                < UNREACHABLE_GRAPH_COST;
    }

    private static boolean hasCompleteRemoteCombatRoute(AgentRuntimeEntry entry,
                                                        Character bot,
                                                        Monster target,
                                                        AgentCombatDecisionContext decisionContext) {
        if (entry == null || bot == null || target == null || target.getPosition() == null) {
            return false;
        }
        GrindGraphContext context = GrindGraphContext.from(decisionContext);
        if (!context.available()) {
            return false;
        }
        int targetRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                context.graph(), entry, context.map(), target.getPosition());
        if (targetRegionId < 0) {
            return false;
        }
        return targetRegionId == context.startRegionId()
                || reliableRouteCost(context, entry, bot, target.getPosition(), targetRegionId)
                < UNREACHABLE_GRAPH_COST;
    }

    private static boolean shouldEscalateToMapWidePreferredTarget(
            AgentRuntimeEntry entry,
            Character bot,
            List<Monster> localCandidates) {
        return AgentCombatObjectiveTargetStateRuntime.hasPreferredTargets(entry)
                && localCandidates.stream().noneMatch(monster ->
                AgentCombatObjectiveTargetStateRuntime.prefers(entry, monster.getId()))
                && !mapWidePreferredTargets(entry, bot).isEmpty();
    }

    private static List<Monster> mapWidePreferredTargets(
            AgentRuntimeEntry entry,
            Character bot) {
        return AgentCombatCandidateProvider.mapWidePreferred(entry, bot);
    }

    private static boolean allowsMapWideSpawnPressure(
            AgentRuntimeEntry entry,
            Character bot,
            long nowMs) {
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        if (directive == null
                || directive.incidentalPolicy()
                != AgentIncidentalMobPolicy.KILL_FOR_SPAWN_PRESSURE) {
            return false;
        }
        GrindGraphContext context = GrindGraphContext.resolve(entry, bot, bot.getPosition());
        int currentRegionId = context.available() ? context.startRegionId() : -1;
        return AgentCombatDirectiveRuntime.state(entry)
                .canSweep(bot.getMapId(), currentRegionId, nowMs);
    }

    private static List<Monster> mapWideSpawnPressureTargets(
            AgentRuntimeEntry entry,
            Character bot) {
        return AgentCombatCandidateProvider.mapWideIncidental(entry, bot);
    }

    private static List<Monster> boundedMapWideSpawnPressureTargets(
            AgentRuntimeEntry entry,
            Character bot) {
        List<Monster> candidates = mapWideSpawnPressureTargets(entry, bot);
        if (candidates.isEmpty()) {
            return candidates;
        }
        GrindGraphContext context = GrindGraphContext.resolve(entry, bot, bot.getPosition());
        if (!context.available()) {
            return candidates;
        }
        Set<Integer> neighborhood = context.graph().getMutualAdjacentRegionIds(context.startRegionId());
        neighborhood.add(context.startRegionId());
        List<Monster> adjacent = candidates.stream()
                .filter(monster -> neighborhood.contains(AgentNavigationRegionService.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), monster.getPosition())))
                .toList();
        if (!adjacent.isEmpty()) {
            return new ArrayList<>(adjacent);
        }

        Map<Monster, Long> routeCosts = new IdentityHashMap<>();
        long cheapest = UNREACHABLE_GRAPH_COST;
        for (Monster candidate : candidates) {
            long routeCost = reliableRouteCost(context, entry, bot, candidate);
            routeCosts.put(candidate, routeCost);
            cheapest = Math.min(cheapest, routeCost);
        }
        if (cheapest >= UNREACHABLE_GRAPH_COST) {
            return List.of();
        }
        long maximumCost = Math.min(UNREACHABLE_GRAPH_COST - 1L,
                cheapest + AgentCombatPolicyConfig.spawnPressureRouteCostWindowMs());
        return candidates.stream()
                .filter(candidate -> routeCosts.getOrDefault(candidate, UNREACHABLE_GRAPH_COST)
                        <= maximumCost)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static Monster findRouteBlockerTarget(AgentRuntimeEntry entry,
                                                 Character bot,
                                                 Point movementTarget,
                                                 AgentCombatConfig.Config config) {
        if (entry == null || bot == null || movementTarget == null) {
            return null;
        }
        Point botPos = bot.getPosition();
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        double range = routeBlockerSeekRange(
                weaponType,
                AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                        + AgentProjectileHitbox.passiveProjectileRangeBonus(bot),
                config);
        List<Monster> candidates = AgentCombatCandidateProvider.local(
                bot, botPos, range * range);
        candidates.removeIf(monster -> !insideRouteCorridor(
                botPos, movementTarget, monster.getPosition(),
                AgentCombatPolicyConfig.routeBlockerCorridorWidth()));
        candidates.removeIf(monster -> !routeBlockerActionableNow(
                entry, bot, monster, config));
        AgentRouteBlockerState blockerState =
                AgentCombatDecisionStateRuntime.state(entry).routeBlocker();
        if (candidates.isEmpty()) {
            blockerState.resumeTravel();
            recordDecision(entry, AgentCombatDecisionTraceState.Mode.ROUTE_BLOCKER,
                    AgentCombatDecisionTraceState.Outcome.NO_CANDIDATES,
                    0, 0, 0, 0, 0, false, false, null);
            return null;
        }
        long nowMs = System.currentTimeMillis();
        if (!blockerState.canInterrupt(movementTarget, nowMs)) {
            AgentCombatDirectiveRuntime.state(entry).selected(
                    bot.getMapId(), -1, 0, AgentCombatCandidateClass.UNRELATED,
                    AgentCombatDecisionReason.EVADE_BLOCKER, nowMs);
            recordDecision(entry, AgentCombatDecisionTraceState.Mode.ROUTE_BLOCKER,
                    AgentCombatDecisionTraceState.Outcome.RESPONSE_DEFERRED,
                    candidates.size(), candidates.size(), candidates.size(), candidates.size(),
                    0, false, false, null);
            return null;
        }
        Monster selected = candidates.stream()
                .min(java.util.Comparator.comparingDouble(
                        monster -> monster.getPosition().distanceSq(botPos)))
                .orElse(null);
        if (selected != null) {
            AgentCombatDirectiveRuntime.state(entry).selected(
                    bot.getMapId(), -1, selected.getId(),
                    AgentCombatCandidateClass.INCIDENTAL,
                    AgentCombatDecisionReason.ROUTE_BLOCKER,
                    nowMs);
        }
        recordDecision(entry, AgentCombatDecisionTraceState.Mode.ROUTE_BLOCKER,
                selected == null
                        ? AgentCombatDecisionTraceState.Outcome.NO_CANDIDATES
                        : AgentCombatDecisionTraceState.Outcome.SELECTED,
                candidates.size(), candidates.size(), candidates.size(), candidates.size(),
                selected == null ? 0 : candidates.size(), false, false, selected);
        return selected;
    }

    static double routeBlockerSeekRange(WeaponType weaponType,
                                        int projectileRange,
                                        AgentCombatConfig.Config config) {
        boolean rangedOrMagic = weaponType == WeaponType.BOW
                || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.CLAW
                || weaponType == WeaponType.GUN
                || weaponType == WeaponType.WAND
                || weaponType == WeaponType.STAFF;
        return rangedOrMagic
                ? Math.max(1, projectileRange)
                : Math.max(1, config.ATTACK_RANGE_X + config.ATTACK_JUMP_X_EXTRA);
    }

    private static boolean routeBlockerActionableNow(AgentRuntimeEntry entry,
                                                     Character bot,
                                                     Monster monster,
                                                     AgentCombatConfig.Config config) {
        AgentAttackPlan attackPlan = AgentCombatPlanRuntime.planAttack(
                entry, bot, monster, config);
        if (attackPlan != null
                && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, bot, monster)) {
            return true;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        AgentAttackRoute route = attackPlan == null
                ? AgentAttackExecutionProvider.determineBasicWeaponRoute(weaponType)
                : attackPlan.route;
        AgentMovementProfile movementProfile = AgentMovementStateRuntime.movementProfile(entry);
        return AgentCombatRangePolicy.isTargetJumpable(
                movementProfile,
                weaponType,
                route,
                bot.getPosition(),
                monster.getPosition(),
                AgentMovementKinematicsService.calculateMaxJumpHeight(movementProfile));
    }

    private static void recordDecision(AgentRuntimeEntry entry,
                                       AgentCombatDecisionTraceState.Mode mode,
                                       AgentCombatDecisionTraceState.Outcome outcome,
                                       int baseCandidates,
                                       int objectiveCandidates,
                                       int policyCandidates,
                                       int claimCandidates,
                                       int scoredCandidates,
                                       boolean mapWidePreferredEscalation,
                                       boolean rankedVariationConsumed,
                                       Monster selected) {
        AgentCombatTargetEvidenceRecorder.decision(
                entry, mode, outcome, baseCandidates, objectiveCandidates, policyCandidates,
                claimCandidates, scoredCandidates, mapWidePreferredEscalation,
                rankedVariationConsumed, selected);
    }

    static boolean insideRouteCorridor(Point start, Point end, Point candidate, int halfWidth) {
        if (start == null || end == null || candidate == null || halfWidth < 0) {
            return false;
        }
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq <= 1.0) {
            return candidate.distanceSq(start) <= (double) halfWidth * halfWidth;
        }
        double projection = ((candidate.x - start.x) * dx + (candidate.y - start.y) * dy)
                / lengthSq;
        if (projection < 0.0 || projection > 1.0) {
            return false;
        }
        double closestX = start.x + projection * dx;
        double closestY = start.y + projection * dy;
        double offX = candidate.x - closestX;
        double offY = candidate.y - closestY;
        return offX * offX + offY * offY <= (double) halfWidth * halfWidth;
    }

    private static void releaseEmptyAutomaticAnchor(AgentRuntimeEntry entry) {
        if (AgentCombatVariationRuntime.isAutomaticPlatformAnchor(entry)) {
            AgentCombatVariationRuntime.clearAutomaticAnchor(entry);
        }
    }

    private static Monster selectVariedTargetWithinWinningRegion(AgentRuntimeEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Foothold botFoothold,
                                                                  List<Monster> candidates,
                                                                  Map<Monster, Integer> targetOccupancy,
                                                                  AgentCombatConfig.Config config,
                                                                  Monster selected,
                                                                  AgentCombatDecisionContext decisionContext) {
        if (selected == null || candidates.size() < 3
                || !AgentCombatVariationRuntime.settings(entry).targetSelectionVariationEnabled()) {
            return selected;
        }

        GrindGraphContext context = GrindGraphContext.from(decisionContext);
        List<Monster> sameReachableRegion = candidates;
        if (context.available()) {
            int selectedRegionId = AgentNavigationRegionService.resolveTargetRegionId(
                    context.graph(), context.entry(), context.map(), selected.getPosition());
            sameReachableRegion = candidates.stream()
                    .filter(candidate -> AgentNavigationRegionService.resolveTargetRegionId(
                            context.graph(), context.entry(), context.map(), candidate.getPosition())
                            == selectedRegionId)
                    .toList();
        }
        if (sameReachableRegion.size() < 3) {
            return selected;
        }

        List<AgentScoredGrindTarget> localScores = scoreLocalTargets(
                entry, bot, botPos, botFoothold, sameReachableRegion, targetOccupancy, config);
        AgentCombatGrindTargetPolicy.sortByTargetOrder(localScores);
        int index = AgentCombatVariationRuntime.selectTargetIndex(
                entry, bot, localScores.size());
        return localScores.get(Math.min(index, localScores.size() - 1)).monster();
    }

    private static RankedTargetSelection selectVariedReachableTarget(
            AgentRuntimeEntry entry,
            Character bot,
            List<AgentScoredGrindTarget> scoredTargets) {
        Monster best = AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(
                scoredTargets, UNREACHABLE_GRAPH_COST);
        if (best == null
                || !AgentCombatVariationRuntime.settings(entry).targetSelectionVariationEnabled()) {
            return new RankedTargetSelection(best, false);
        }
        List<AgentScoredGrindTarget> reachableTargets = scoredTargets.stream()
                .filter(target -> target.routeCost() < UNREACHABLE_GRAPH_COST)
                .toList();
        if (reachableTargets.size() < 3) {
            return new RankedTargetSelection(best, false);
        }
        int index = AgentCombatVariationRuntime.selectTargetIndex(
                entry, bot, reachableTargets.size());
        return new RankedTargetSelection(
                reachableTargets.get(Math.min(index, reachableTargets.size() - 1)).monster(), true);
    }

    private static int targetRegionId(AgentCombatDecisionContext decisionContext,
                                      Monster target) {
        if (target == null || !decisionContext.available()) {
            return -1;
        }
        return AgentNavigationRegionService.resolveTargetRegionId(
                decisionContext.graph(), decisionContext.entry(), decisionContext.map(),
                target.getPosition());
    }

    private record RankedTargetSelection(Monster target, boolean variationDecisionConsumed) {
    }

    private static boolean isLocalCombatTarget(GrindGraphContext context,
                                               Character bot,
                                               Foothold botFoothold,
                                               Monster target) {
        Foothold targetFoothold = botFoothold == null
                ? null
                : AgentCombatGroundRuntime.findGroundFoothold(target.getPosition(), bot);
        return AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                botFoothold,
                targetFoothold,
                context.available(),
                () -> AgentNavigationRegionService.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), target.getPosition()),
                context.startRegionId());
    }

    private static List<AgentScoredGrindTarget> scoreLocalTargets(AgentRuntimeEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Foothold botFoothold,
                                                                  List<Monster> candidates,
                                                                  Map<Monster, Integer> targetOccupancy,
                                                                  AgentCombatConfig.Config config) {
        return AgentCombatGrindTargetPolicy.scoreLocalTargets(
                candidates,
                botPos,
                candidate -> grindTargetScore(
                        bot, botPos, botFoothold, candidate, targetOccupancy, config),
                candidate -> AgentCombatScoringPolicy.aoeClusterBonus(
                        candidate,
                        candidates,
                        entry != null && AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        entry == null ? 0 : AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
    }

    private static List<AgentScoredGrindTarget> scoreTargetRegions(AgentRuntimeEntry entry,
                                                                   GrindGraphContext context,
                                                                   Character bot,
                                                                   Point botPos,
                                                                   Foothold botFoothold,
                                                                   List<Monster> candidates,
                                                                   Map<Monster, Integer> targetOccupancy,
                                                                   AgentCombatConfig.Config config) {
        return AgentCombatGrindTargetPolicy.scoreTargetRegions(
                candidates,
                botPos,
                candidate -> AgentNavigationRegionService.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), candidate.getPosition()),
                candidate -> grindTargetScore(
                        bot, botPos, botFoothold, candidate, targetOccupancy, config)
                        - AgentCombatScoringPolicy.aoeClusterBonus(
                        candidate,
                        candidates,
                        entry != null && AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        entry == null ? 0 : AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)),
                group -> AgentCombatScoringPolicy.addReachableGraphPenalty(
                        reliableRouteCost(context, entry, bot,
                                group.bestMonster().getPosition(), group.regionId()),
                        AgentCombatScoringPolicy.upwardPlatformPenalty(
                                botPos, group.bestMonster().getPosition()),
                        UNREACHABLE_GRAPH_COST),
                group -> grindRegionOccupancyPenalty(context, bot, group.regionId(), config),
                UNREACHABLE_GRAPH_COST);
    }

    static long combatRouteCost(AgentNavigationPathService.SearchOutcome outcome,
                                long unreachableCost) {
        return AgentNavigationPathService.completeRouteCost(outcome, unreachableCost);
    }

    private static long grindTargetScore(Character bot,
                                         Point botPos,
                                         Foothold botFoothold,
                                         Monster target,
                                         Map<Monster, Integer> targetOccupancy,
                                         AgentCombatConfig.Config config) {
        Point targetPos = target.getPosition();
        Foothold targetFoothold = AgentCombatGroundRuntime.findGroundFoothold(targetPos, bot);

        boolean sameFoothold = botFoothold != null && targetFoothold != null
                && botFoothold.getId() == targetFoothold.getId();
        return AgentCombatScoringPolicy.localTargetScore(botPos, targetPos, sameFoothold, config.ATTACK_RANGE_Y)
                + AgentCombatGrindTargetPolicy.occupancyPenalty(
                        targetOccupancy.getOrDefault(target, 0),
                        config.GRIND_TARGET_OCCUPANCY_PENALTY,
                        config.GRIND_TARGET_OCCUPANCY_PENALTY_CAP);
    }

    private static Map<Monster, Integer> grindTargetOccupancy(
            AgentRuntimeEntry entry,
            Character bot,
            AgentCombatDecisionContext decisionContext) {
        if (entry == null || bot == null || bot.getMap() == null) {
            return Map.of();
        }
        Map<Monster, Integer> occupancy = new IdentityHashMap<>();
        Map<Integer, Monster> monstersByObjectId = AgentCombatCandidateProvider.byObjectId(bot);
        for (AgentPeerPerception peer : decisionContext.perception().agentPeers()) {
            Monster siblingTarget = monstersByObjectId.get(peer.targetObjectId());
            if (peer.characterId() != bot.getId() && peer.grinding() && siblingTarget != null) {
                occupancy.merge(siblingTarget, 1, Integer::sum);
            }
        }
        return occupancy;
    }

    private static boolean territorialFieldAssignment(
            AgentRuntimeEntry entry, Character bot, long nowMs) {
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        var assignment = directive == null ? null : directive.regionAssignment();
        return assignment != null && assignment.territorial()
                && bot != null && assignment.mapId() == bot.getMapId()
                && nowMs < assignment.expiresAtMs();
    }

    private static PolicySelection applyObjectivePolicy(AgentRuntimeEntry entry,
                                                        Character bot,
                                                        Point botPos,
                                                        Foothold botFoothold,
                                                        List<Monster> candidates,
                                                        boolean commitLocalPlatformBatch,
                                                        AgentCombatDecisionContext decisionContext) {
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        if (directive == null) {
            return fallbackPolicySelection(entry, candidates);
        }

        GrindGraphContext context = GrindGraphContext.from(decisionContext);
        int currentRegionId = context.available() ? context.startRegionId() : -1;
        long nowMs = decisionContext.capturedAtMs();
        boolean allowSweep = directive.incidentalPolicy()
                == AgentIncidentalMobPolicy.KILL_FOR_SPAWN_PRESSURE
                && AgentCombatDirectiveRuntime.state(entry)
                .canSweep(bot.getMapId(), currentRegionId, nowMs);
        AgentQuestLocalClearTargetPolicy.Selection<Monster> selected =
                AgentQuestLocalClearTargetPolicy.select(
                        candidates,
                        monster -> directive.requiredMobIds().contains(monster.getId()),
                        monster -> isLocalCombatTarget(context, bot, botFoothold, monster),
                        allowSweep,
                        commitLocalPlatformBatch);
        return new PolicySelection(new ArrayList<>(selected.candidates()),
                selected.candidateClass(), selected.reason(), currentRegionId);
    }

    private static PlatformBatchSelection retainPlatformBatchCandidates(
            AgentRuntimeEntry entry,
            Character bot,
            List<Monster> candidates,
            long nowMs,
            AgentCombatDecisionContext decisionContext) {
        if (entry == null || bot == null) {
            return new PlatformBatchSelection(candidates, false);
        }
        String objectiveId = AgentProgressionEventPublisher.objectiveId(entry);
        AgentCombatPlatformBatchState state =
                AgentCombatDecisionStateRuntime.state(entry).platformBatch();
        if (!state.active(bot.getMapId(), objectiveId, nowMs)) {
            return new PlatformBatchSelection(candidates, false);
        }
        GrindGraphContext context = GrindGraphContext.from(decisionContext);
        List<Monster> retained = candidates.stream()
                .filter(monster -> state.includes(
                        bot.getMapId(), objectiveId,
                        context.available()
                                ? AgentNavigationRegionService.resolveTargetRegionId(
                                context.graph(), context.entry(), context.map(), monster.getPosition())
                                : -1,
                        monster.getPosition(), nowMs,
                        AgentCombatPolicyConfig.platformBatchRadiusPx(),
                        AgentCombatPolicyConfig.platformBatchYTolerancePx()))
                .toList();
        if (retained.isEmpty()) {
            state.release();
            return new PlatformBatchSelection(candidates, false);
        }
        AgentCombatTargetSearchModeState searchMode = searchModeState(entry, bot, nowMs);
        if (searchMode != null) {
            searchMode.enter(AgentCombatTargetSearchMode.REGION_HARVEST,
                    "clearing a bounded same-platform combat batch",
                    state.snapshot(nowMs).regionId(), nowMs);
        }
        return new PlatformBatchSelection(new ArrayList<>(retained), true);
    }

    private static void beginPlatformBatch(AgentRuntimeEntry entry,
                                           Character bot,
                                           Monster selected,
                                           long nowMs,
                                           AgentCombatDecisionContext decisionContext) {
        if (entry == null || bot == null || bot.getMap() == null
                || selected == null || selected.getPosition() == null) {
            return;
        }
        String objectiveId = AgentProgressionEventPublisher.objectiveId(entry);
        AgentCombatPlatformBatchState state =
                AgentCombatDecisionStateRuntime.state(entry).platformBatch();
        if (state.active(bot.getMapId(), objectiveId, nowMs)) {
            return;
        }
        Point anchor = selected.getPosition();
        GrindGraphContext context = GrindGraphContext.from(decisionContext);
        int selectedRegionId = context.available()
                ? AgentNavigationRegionService.resolveTargetRegionId(
                context.graph(), context.entry(), context.map(), anchor)
                : -1;
        long radiusSquared = (long) AgentCombatPolicyConfig.platformBatchRadiusPx()
                * AgentCombatPolicyConfig.platformBatchRadiusPx();
        int clusterSize = (int) AgentMapPerception.monsters(bot.getMap()).stream()
                .filter(AgentCombatTargetEligibilityPolicy::isHostileLivingMonster)
                .filter(monster -> AgentCombatObjectiveTargetStateRuntime.allows(
                        entry, monster.getId()))
                .filter(monster -> monster.getPosition() != null)
                .filter(monster -> anchor.distanceSq(monster.getPosition()) <= radiusSquared)
                .filter(monster -> {
                    int candidateRegionId = context.available()
                            ? AgentNavigationRegionService.resolveTargetRegionId(
                            context.graph(), context.entry(), context.map(), monster.getPosition())
                            : -1;
                    return selectedRegionId >= 0 && candidateRegionId >= 0
                            ? selectedRegionId == candidateRegionId
                            : Math.abs(anchor.y - monster.getPosition().y)
                            <= AgentCombatPolicyConfig.platformBatchYTolerancePx();
                })
                .limit(AgentCombatPolicyConfig.platformBatchMaxKills())
                .count();
        if (clusterSize < 2) {
            return;
        }
        state.begin(bot.getMapId(), objectiveId, selectedRegionId, anchor,
                Math.min(clusterSize, AgentCombatPolicyConfig.platformBatchMaxKills()),
                nowMs, AgentCombatPolicyConfig.platformBatchLeaseMs());
        AgentCombatTargetSearchModeState searchMode = searchModeState(entry, bot, nowMs);
        if (searchMode != null) {
            searchMode.enter(AgentCombatTargetSearchMode.REGION_HARVEST,
                    "queued " + clusterSize + " eligible mobs on the selected platform",
                    selectedRegionId, nowMs);
        }
    }

    private static PolicySelection fallbackPolicySelection(
            AgentRuntimeEntry entry, List<Monster> candidates) {
        List<Monster> preferred = candidates;
        if (AgentCombatObjectiveTargetStateRuntime.hasPreferredTargets(entry)) {
            List<Monster> required = candidates.stream()
                    .filter(monster -> AgentCombatObjectiveTargetStateRuntime.prefers(
                            entry, monster.getId()))
                    .toList();
            if (!required.isEmpty()) {
                preferred = new ArrayList<>(required);
            }
        }
        return new PolicySelection(preferred, AgentCombatCandidateClass.REQUIRED,
                AgentCombatDecisionReason.CLOSEST_ELIGIBLE, -1);
    }

    private static void recordPolicySelection(AgentRuntimeEntry entry,
                                              Character bot,
                                              Monster selected,
                                              PolicySelection policySelection) {
        if (policySelection == null) {
            return;
        }
        AgentCombatTargetEvidenceRecorder.policySelection(
                entry, bot, selected, policySelection.candidateClass(),
                policySelection.reason(), policySelection.regionId());
    }

    private static void synchronizeSearchMode(AgentRuntimeEntry entry,
                                              Character bot,
                                              long nowMs) {
        AgentCombatTargetEvidenceRecorder.synchronize(entry, bot, nowMs);
    }

    private static AgentCombatTargetSearchModeState searchModeState(AgentRuntimeEntry entry,
                                                                     Character bot,
                                                                     long nowMs) {
        return AgentCombatTargetEvidenceRecorder.state(entry, bot, nowMs);
    }

    private static void recordSearchRanking(AgentRuntimeEntry entry,
                                            Character bot,
                                            List<AgentScoredGrindTarget> scoredTargets,
                                            int localCandidateCount,
                                            int preferredCandidateCount,
                                            AgentCombatDecisionContext decisionContext) {
        AgentCombatTargetEvidenceRecorder.searchRanking(
                entry, bot, scoredTargets, localCandidateCount, preferredCandidateCount,
                target -> targetRegionId(decisionContext, target));
    }

    private record PolicySelection(List<Monster> candidates,
                                   AgentCombatCandidateClass candidateClass,
                                   AgentCombatDecisionReason reason,
                                   int regionId) {
    }

    private record TargetPromotion(List<Monster> candidates, boolean mapWide) {
    }

    private record PlatformBatchSelection(List<Monster> candidates, boolean retained) {
    }

    /** One immutable live-world capture used throughout a combat target decision. */
    private record AgentCombatDecisionContext(
            AgentRuntimeEntry entry,
            Character agent,
            MapleMap map,
            AgentNavigationGraph graph,
            AgentMovementProfile profile,
            Point startPos,
            int startRegionId,
            AgentPerceptionSnapshot perception,
            Map<Integer, Long> routeCosts,
            long capturedAtMs) {

        static AgentCombatDecisionContext capture(
                AgentRuntimeEntry entry,
                Character agent,
                long nowMs) {
            Point position = agent == null || agent.getPosition() == null
                    ? null : new Point(agent.getPosition());
            MapleMap map = agent == null ? null : agent.getMap();
            AgentMovementProfile profile =
                    AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent);
            AgentPerceptionSnapshot perception = agent == null
                    ? AgentPerceptionSnapshot.unavailable()
                    : CosmicAgentPerceptionSnapshotFactory.capture(agent, nowMs);
            if (map == null || position == null || map.getFootholds() == null) {
                return new AgentCombatDecisionContext(
                        entry, agent, map, null, profile, position, -1, perception,
                        new java.util.HashMap<>(), nowMs);
            }

            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(entry, map, profile);
                graph = AgentNavigationGraphService.peekClosestGraph(map, profile);
            }
            int startRegionId = graph == null ? -1
                    : AgentNavigationRegionService.resolveCurrentRegionId(
                    graph, entry, map, position);
            return new AgentCombatDecisionContext(
                    entry, agent, map, graph, profile, position,
                    startRegionId, perception, new java.util.HashMap<>(), nowMs);
        }

        boolean available() {
            return graph != null && map != null && startPos != null && startRegionId >= 0;
        }
    }

    private static long grindRegionOccupancyPenalty(GrindGraphContext context, Character bot, int targetRegionId,
                                                    AgentCombatConfig.Config config) {
        if (!context.available() || bot == null || targetRegionId < 0) {
            return 0L;
        }

        int occupiedCount = 0;
        AgentPerceptionSnapshot snapshot = context.perception();
        for (AgentPeerPerception peer : snapshot.agentPeers()) {
            boolean self = peer.characterId() == bot.getId();
            if (!AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                    self, peer.grinding(), true, true, peer.position() != null)) {
                continue;
            }

            int occupiedRegionId = AgentNavigationRegionService.resolvePointTargetRegionId(
                    context.graph(), context.map(), new Point(peer.position().x(), peer.position().y()));
            if (AgentCombatGrindTargetPolicy.shouldCountRegionOccupant(occupiedRegionId, targetRegionId)) {
                occupiedCount++;
            }
        }

        return AgentCombatGrindTargetPolicy.occupancyPenalty(occupiedCount,
                config.GRIND_REGION_OCCUPANCY_PENALTY, config.GRIND_REGION_OCCUPANCY_PENALTY_CAP);
    }

    private record GrindGraphContext(AgentRuntimeEntry entry,
                                     MapleMap map,
                                     AgentNavigationGraph graph,
                                     AgentMovementProfile profile,
                                     Point startPos,
                                     int startRegionId,
                                     AgentPerceptionSnapshot perception,
                                     Map<Integer, Long> routeCosts) {
        static GrindGraphContext from(AgentCombatDecisionContext context) {
            return new GrindGraphContext(
                    context.entry(), context.map(), context.graph(), context.profile(),
                    context.startPos(), context.startRegionId(), context.perception(),
                    context.routeCosts());
        }

        static GrindGraphContext resolve(AgentRuntimeEntry entry, Character bot, Point botPos) {
            if (entry == null || bot == null || bot.getMap() == null || bot.getMap().getFootholds() == null) {
                return unavailable(entry, bot, botPos);
            }

            AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(bot.getMap(), profile);
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(entry, bot.getMap(), profile);
                graph = AgentNavigationGraphService.peekClosestGraph(bot.getMap(), profile);
            }
            if (graph == null) {
                return unavailable(entry, bot, botPos);
            }

            int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            if (startRegionId < 0) {
                return unavailable(entry, bot, botPos);
            }
            return new GrindGraphContext(entry, bot.getMap(), graph, profile, new Point(botPos),
                    startRegionId,
                    CosmicAgentPerceptionSnapshotFactory.capture(bot, System.currentTimeMillis()),
                    new java.util.HashMap<>());
        }

        private static GrindGraphContext unavailable(AgentRuntimeEntry entry, Character bot, Point botPos) {
            MapleMap map = bot == null ? null : bot.getMap();
            AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            Point startPos = botPos == null ? null : new Point(botPos);
            return new GrindGraphContext(entry, map, null, profile, startPos, -1,
                    AgentPerceptionSnapshot.unavailable(), new java.util.HashMap<>());
        }

        boolean available() {
            return graph != null && map != null && startPos != null && startRegionId >= 0 && entry != null;
        }
    }
}
