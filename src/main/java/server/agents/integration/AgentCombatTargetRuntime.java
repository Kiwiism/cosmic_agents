package server.agents.integration;

import server.agents.capabilities.supplies.AgentAmmoStateRuntime;

import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatGrindTargetPolicy;
import server.agents.capabilities.combat.AgentCombatImmediateTargetPolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.capabilities.combat.AgentGrindTargetGroup;
import server.agents.capabilities.combat.AgentProjectileHitbox;
import server.agents.capabilities.combat.AgentScoredGrindTarget;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AgentCombatTargetRuntime {
    private static final long UNREACHABLE_GRAPH_COST = Long.MAX_VALUE / 4;

    private AgentCombatTargetRuntime() {
    }

    public static Monster findGrindTarget(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double rangeSq = (double) config.GRIND_SEEK_RANGE * config.GRIND_SEEK_RANGE;
            Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, rangeSq);
            if (candidates.isEmpty()) return null;

            List<AgentScoredGrindTarget> scoredTargets = scoreGrindTargets(
                    entry, bot, botPos, botFoothold, candidates, config);
            if (scoredTargets.isEmpty()) {
                return null;
            }

            return AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(scoredTargets, UNREACHABLE_GRAPH_COST);
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
            Point botPos = bot.getPosition();
            double rangeSq = (double) config.GRIND_SEEK_RANGE * config.GRIND_SEEK_RANGE;
            Foothold botFoothold = AgentCombatGroundRuntime.findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, rangeSq);
            if (candidates.isEmpty()) {
                return null;
            }
            GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
            if (!graphContext.available()) {
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
                for (Monster m : candidates) {
                    int mId = graph.findRegionId(map, m.getPosition());
                    if (mId == patrolId || adjacentIds.contains(mId)) {
                        filtered.add(m);
                    }
                }
            }
            if (filtered.isEmpty()) {
                return null;
            }

            List<AgentScoredGrindTarget> scored = scoreGrindTargets(entry, bot, botPos, botFoothold, filtered, config);
            if (scored.isEmpty()) {
                return null;
            }
            return AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(scored, UNREACHABLE_GRAPH_COST);
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
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, range * range);
            if (candidates.isEmpty()) {
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
                    candidate -> grindTargetScore(bot, botPos, botFoothold, candidate, config),
                    candidate -> AgentCombatScoringPolicy.legacyAoeClusterBonus(
                            candidate,
                            candidates,
                            entry != null && AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                            entry == null ? 0 : AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
            return AgentCombatGrindTargetPolicy.pickFromBestTargets(localTargets);
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
                targetCost = graphPathCost(
                        graphContext.graph(),
                        graphContext.map(),
                        graphContext.startPos(),
                        graphContext.startRegionId(),
                        targetPos,
                        targetRegionId,
                        graphContext.profile());
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

    private static List<AgentScoredGrindTarget> scoreGrindTargets(AgentRuntimeEntry entry,
                                                                  Character bot,
                                                                  Point botPos,
                                                                  Foothold botFoothold,
                                                                  List<Monster> candidates,
                                                                  AgentCombatConfig.Config config) {
        GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
        return AgentCombatGrindTargetPolicy.scoreGrindTargets(
                graphContext.available(),
                () -> scoreLocalTargets(entry, bot, botPos, botFoothold, candidates, config),
                () -> scoreTargetRegions(entry, graphContext, bot, botPos, botFoothold, candidates, config));
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
                                                                  AgentCombatConfig.Config config) {
        return AgentCombatGrindTargetPolicy.scoreLocalTargets(
                candidates,
                botPos,
                candidate -> grindTargetScore(bot, botPos, botFoothold, candidate, config),
                candidate -> AgentCombatScoringPolicy.legacyAoeClusterBonus(
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
                                                                   AgentCombatConfig.Config config) {
        return AgentCombatGrindTargetPolicy.scoreTargetRegions(
                candidates,
                botPos,
                candidate -> AgentNavigationRegionService.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), candidate.getPosition()),
                candidate -> grindTargetScore(bot, botPos, botFoothold, candidate, config)
                        - AgentCombatScoringPolicy.legacyAoeClusterBonus(
                        candidate,
                        candidates,
                        entry != null && AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        entry == null ? 0 : AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)),
                group -> graphPathCost(context.graph(), context.map(), context.startPos(), context.startRegionId(),
                        group.bestMonster().getPosition(), group.regionId(), context.profile()),
                group -> grindRegionOccupancyPenalty(context, bot, group.regionId(), config),
                UNREACHABLE_GRAPH_COST);
    }

    private static long graphPathCost(AgentNavigationGraph graph,
                                      MapleMap map,
                                      Point startPos,
                                      int startRegionId,
                                      Point targetPos,
                                      int targetRegionId,
                                      AgentMovementProfile profile) {
        if (startPos == null || targetPos == null || startRegionId < 0 || targetRegionId < 0) {
            return AgentCombatGrindTargetPolicy.graphPathCost(false, false, 0L, List.of(), UNREACHABLE_GRAPH_COST);
        }
        if (startRegionId == targetRegionId) {
            return AgentCombatGrindTargetPolicy.graphPathCost(true, true,
                    AgentCombatScoringPolicy.estimateLocalTravelCostMs(startPos, targetPos, profile),
                    List.of(), UNREACHABLE_GRAPH_COST);
        }

        List<AgentNavigationGraph.Edge> path = AgentNavigationPathService.findPathForTargetScore(
                graph, map, startPos, startRegionId, targetRegionId, targetPos);
        List<Long> edgeCosts = new ArrayList<>(path.size());
        for (AgentNavigationGraph.Edge edge : path) {
            edgeCosts.add((long) edge.cost);
        }
        return AgentCombatGrindTargetPolicy.graphPathCost(true, false, 0L, edgeCosts, UNREACHABLE_GRAPH_COST);
    }

    private static long grindTargetScore(Character bot, Point botPos, Foothold botFoothold, Monster target,
                                         AgentCombatConfig.Config config) {
        Point targetPos = target.getPosition();
        Foothold targetFoothold = AgentCombatGroundRuntime.findGroundFoothold(targetPos, bot);

        boolean sameFoothold = botFoothold != null && targetFoothold != null
                && botFoothold.getId() == targetFoothold.getId();
        return AgentCombatScoringPolicy.localTargetScore(botPos, targetPos, sameFoothold, config.ATTACK_RANGE_Y);
    }

    private static long grindRegionOccupancyPenalty(GrindGraphContext context, Character bot, int targetRegionId,
                                                    AgentCombatConfig.Config config) {
        Character owner = AgentRuntimeIdentityRuntime.owner(context.entry());
        if (!context.available() || owner == null || bot == null || targetRegionId < 0) {
            return 0L;
        }

        int occupiedCount = 0;
        for (AgentRuntimeEntry sibling : AgentSessionLifecycleSideEffects.getBotEntries(owner.getId())) {
            if (sibling == context.entry() || sibling == null || !AgentModeStateRuntime.grinding(sibling)) {
                continue;
            }
            Character siblingBot = AgentRuntimeIdentityRuntime.bot(sibling);
            if (siblingBot == null) {
                continue;
            }
            boolean sameMap = siblingBot.getMap() == context.map();
            boolean alive = siblingBot.getHp() > 0;
            boolean hasPosition = siblingBot.getPosition() != null;
            if (!AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                    sibling == context.entry(),
                    AgentModeStateRuntime.grinding(sibling),
                    sameMap,
                    alive,
                    hasPosition)) {
                continue;
            }

            int occupiedRegionId = AgentNavigationRegionService.resolveCurrentRegionId(
                    context.graph(), sibling, context.map(), siblingBot.getPosition());
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
                                     int startRegionId) {
        static GrindGraphContext resolve(AgentRuntimeEntry entry, Character bot, Point botPos) {
            if (entry == null || bot == null || bot.getMap() == null || bot.getMap().getFootholds() == null) {
                return unavailable(entry, bot, botPos);
            }

            AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(bot.getMap(), profile);
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(bot.getMap(), profile);
                graph = AgentNavigationGraphService.peekClosestGraph(bot.getMap(), profile);
            }
            if (graph == null) {
                return unavailable(entry, bot, botPos);
            }

            int startRegionId = AgentNavigationRegionService.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            if (startRegionId < 0) {
                return unavailable(entry, bot, botPos);
            }
            return new GrindGraphContext(entry, bot.getMap(), graph, profile, new Point(botPos), startRegionId);
        }

        private static GrindGraphContext unavailable(AgentRuntimeEntry entry, Character bot, Point botPos) {
            MapleMap map = bot == null ? null : bot.getMap();
            AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            Point startPos = botPos == null ? null : new Point(botPos);
            return new GrindGraphContext(entry, map, null, profile, startPos, -1);
        }

        boolean available() {
            return graph != null && map != null && startPos != null && startRegionId >= 0 && entry != null;
        }
    }
}
