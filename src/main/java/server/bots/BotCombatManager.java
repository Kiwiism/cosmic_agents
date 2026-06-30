package server.bots;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackPlanScoringPolicy;
import server.agents.capabilities.combat.AgentAttackPlanTieBreakPolicy;
import server.agents.capabilities.combat.AgentBasicAttackPlanRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.capabilities.combat.AgentCombatImmediateTargetPolicy;
import server.agents.capabilities.combat.AgentCombatGrindTargetPolicy;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.capabilities.combat.AgentProjectileHitbox;
import server.agents.capabilities.combat.AgentScoredGrindTarget;
import server.agents.capabilities.combat.AgentSkillAttackPlanRuntime;
import server.agents.capabilities.combat.AgentGrindTargetGroup;

import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.Corsair;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.Fighter;
import constants.skills.GM;
import constants.skills.Marksman;
import constants.skills.Priest;
import constants.skills.Spearman;
import constants.skills.ThunderBreaker;
import constants.skills.WhiteKnight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSkillBuffDebugStateRuntime;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;
import tools.Pair;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class BotCombatManager {
    private static final Logger log = LoggerFactory.getLogger(BotCombatManager.class);
    private static final long UNREACHABLE_GRAPH_COST = Long.MAX_VALUE / 4;

    public static final class AttackPlan extends AgentAttackPlan {
        AttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                   AgentAttackRoute route, int display, int direction, int rangedDirection, int stance, int speed,
                   int hitDelayMs, int cooldownMs, WeaponType damageWeaponType) {
            super(skillId, skillLevel, numDamage, hitBox, targets, route, display, direction, rangedDirection,
                    stance, speed, hitDelayMs, cooldownMs, damageWeaponType);
        }
    }

    public static AgentCombatConfig.Config cfg = AgentCombatConfig.cfg;

    static void tickMobDamage(BotEntry entry, Character bot) {
        AgentBotCombatDamageRuntime.tickMobDamage(entry, bot, cfg, BotMovementManager::tickDown);
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Uses the bot's shared character WDEF cache instead of ignoring defense entirely.
     */
    static void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        AgentBotCombatDamageRuntime.applyMobHit(entry, bot, mob, cfg);
    }

    /**
     * Apply fall damage on landing. Distance is peak-to-landing descent in pixels
     * (BotPhysicsEngine tracks fall peak physics state through Agent movement
     * adapters each airborne tick and passes the delta here). Distance-based
     * rather than velocity-based because
     * terminal velocity is reached after only ~112px of fall, so velocity saturates
     * immediately while real-client damage keeps scaling with drop height.
     *
     * No packet is broadcast below threshold — matches real-client behaviour for
     * small jumps (no DAMAGE_PLAYER observed in monitored-packets logs).
     *
     * Broadcast direction is hardcoded to 0 because every captured real-client fall
     * sample used direction=0. Physics knockback still derives from bot facing so
     * the recoil arc points backward along the bot's movement direction.
     */
    static void applyFallDamage(BotEntry entry, Character bot, float fallDistancePx) {
        AgentBotCombatDamageRuntime.applyFallDamage(entry, bot, fallDistancePx, cfg);
    }

    static void enterDeadState(BotEntry entry, Character bot, boolean announceDeath) {
        AgentBotCombatDeathRuntime.enterDeadState(entry, bot, announceDeath, cfg);
    }

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
    }

    static void tickBuffs(BotEntry entry, Character bot) {
        AgentBotCombatBuffRuntime.tickBuffs(entry, bot, cfg);
    }

    static boolean tickSupportHealing(BotEntry entry, Character bot) {
        return AgentBotCombatHealRuntime.tickSupportHealing(entry, bot, cfg);
    }

    /** Returns the most convenient reachable target (deterministic — closest/best score wins). */
    public static Monster findGrindTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Foothold botFoothold = findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, rangeSq);
            if (candidates.isEmpty()) return null;

            List<AgentScoredGrindTarget> scoredTargets = scoreGrindTargets(entry, bot, botPos, botFoothold, candidates);
            if (scoredTargets.isEmpty()) {
                return null;
            }

            return AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(scoredTargets, UNREACHABLE_GRAPH_COST);
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    /**
     * Patrol mode: find the best grind target restricted to the patrol region and its
     * immediate neighbours (1 graph hop). Tries the home region first; expands to
     * adjacent regions only when the home region has no candidates.
     */
    static Monster findPatrolTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            if (entry == null || bot == null || !AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
                return null;
            }
            Point botPos = bot.getPosition();
            double rangeSq = (double) cfg.GRIND_SEEK_RANGE * cfg.GRIND_SEEK_RANGE;
            Foothold botFoothold = findGroundFoothold(botPos, bot);
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, rangeSq);
            if (candidates.isEmpty()) {
                return null;
            }
            GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
            if (!graphContext.available()) {
                return null;
            }
            BotNavigationGraph graph = graphContext.graph();
            MapleMap map = graphContext.map();
            int patrolId = AgentBotPatrolStateRuntime.patrolRegionId(entry);

            // 1-hop expansion: only inter-region edges count as a hop. Self-loop edges
            // (intra-region portals where fromRegionId == toRegionId) are free traversals
            // within the patrol region itself — A* uses them to shortcut long walks but
            // they don't expose new regions, so skip them here to keep intent explicit.
            Set<Integer> adjacentIds = graph.getMutualAdjacentRegionIds(patrolId);

            // Phase 1: home region only
            List<Monster> filtered = new ArrayList<>();
            for (Monster m : candidates) {
                if (graph.findRegionId(map, m.getPosition()) == patrolId) {
                    filtered.add(m);
                }
            }
            // Phase 2: expand to adjacent if home region is empty
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

            List<AgentScoredGrindTarget> scored = scoreGrindTargets(entry, bot, botPos, botFoothold, filtered);
            if (scored.isEmpty()) {
                return null;
            }
            return AgentCombatGrindTargetPolicy.pickReachableOrBestTarget(scored, UNREACHABLE_GRAPH_COST);
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    /** Follow mode should only attack local mobs; it should not run pathfinding or chase across the map. */
    static Monster findFollowAttackTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double range = Math.max(
                    AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                            + AgentProjectileHitbox.passiveProjectileRangeBonus(bot),
                    BotCombatManager.cfg.ATTACK_RANGE_X + BotCombatManager.cfg.ATTACK_JUMP_X_EXTRA);
            List<Monster> candidates = AgentCombatTargetSelector.aliveMonstersInRange(bot, botPos, range * range);
            if (candidates.isEmpty()) {
                return null;
            }

            Foothold botFoothold = findGroundFoothold(botPos, bot);
            GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
            List<AgentScoredGrindTarget> localTargets = AgentCombatGrindTargetPolicy.scoreFollowLocalTargets(
                    candidates,
                    botPos,
                    candidate -> isLocalCombatTarget(graphContext, bot, botFoothold, candidate)
                    || AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(
                            bot,
                            candidate,
                            entry == null || AgentBotAmmoStateRuntime.noAmmo(entry),
                            entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry)),
                    candidate -> grindTargetScore(bot, botPos, botFoothold, candidate),
                    candidate -> AgentCombatScoringPolicy.legacyAoeClusterBonus(
                            candidate,
                            candidates,
                            entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                            entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
            return AgentCombatGrindTargetPolicy.pickFromBestTargets(localTargets);
        } finally {
            AgentPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    static boolean isReachableGrindTarget(BotEntry entry, Character bot, Monster target) {
        boolean targetPresentAndAlive = target != null && target.isAlive();
        boolean hasRuntimeContext = entry != null && bot != null;
        GrindGraphContext graphContext = targetPresentAndAlive && hasRuntimeContext
                ? GrindGraphContext.resolve(entry, bot, bot.getPosition())
                : null;
        boolean immediateProjectileTarget = targetPresentAndAlive && hasRuntimeContext
                && AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(
                        bot,
                        target,
                        entry == null || AgentBotAmmoStateRuntime.noAmmo(entry),
                        entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry));
        boolean graphAvailable = graphContext != null && graphContext.available();
        long targetCost = UNREACHABLE_GRAPH_COST;
        if (targetPresentAndAlive && hasRuntimeContext && !immediateProjectileTarget && graphAvailable) {
            Point targetPos = target.getPosition();
            int targetRegionId = BotNavigationManager.resolveTargetRegionId(
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

    public static AttackPlan planAttack(BotEntry entry, Character bot, Monster target) {
        long startedAt = System.nanoTime();
        try {
            List<AttackPlan> candidates = new ArrayList<>(3);

            for (int skillId : AgentCombatSkillClassifier.cachedAttackSkillIds(
                    AgentBotCombatSkillCacheStateRuntime.attackSkillIds(entry),
                    AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry),
                    AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry))) {
                AttackPlan skillAttack = planSkillAttack(entry, bot, target, skillId);
                if (skillAttack != null) {
                    candidates.add(skillAttack);
                }
            }

            AttackPlan basicAttack = planBasicAttack(bot, target);
            if (basicAttack != null) {
                candidates.add(basicAttack);
            }
            return AgentAttackPlanScoringPolicy.selectBestAttackPlan(bot, candidates);
        } finally {
            AgentPerformanceMonitor.record("combat-plan", System.nanoTime() - startedAt);
        }
    }

    private static AttackPlan planBasicAttack(Character bot, Monster target) {
        return toBotAttackPlan(AgentBasicAttackPlanRuntime.planBasicAttack(bot, target));
    }

    private static AttackPlan toBotAttackPlan(AgentAttackPlan plan) {
        if (plan == null) {
            return null;
        }
        return new AttackPlan(plan.skillId, plan.skillLevel, plan.numDamage, plan.hitBox, plan.targets,
                plan.route, plan.display, plan.direction, plan.rangedDirection, plan.stance, plan.speed,
                plan.hitDelayMs, plan.cooldownMs, plan.damageWeaponType);
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        AgentBotCombatAttackRuntime.attackMonster(entry, bot, attackPlan);
    }

    private static AttackPlan planSkillAttack(BotEntry entry, Character bot, Monster primaryTarget, int skillId) {
        return toBotAttackPlan(AgentSkillAttackPlanRuntime.planSkillAttack(bot, primaryTarget, skillId, cfg));
    }

    private static List<AgentScoredGrindTarget> scoreGrindTargets(BotEntry entry,
                                                             Character bot,
                                                             Point botPos,
                                                             Foothold botFoothold,
                                                             List<Monster> candidates) {
        GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
        return AgentCombatGrindTargetPolicy.scoreGrindTargets(
                graphContext.available(),
                () -> scoreLocalTargets(entry, bot, botPos, botFoothold, candidates),
                () -> scoreTargetRegions(entry, graphContext, bot, botPos, botFoothold, candidates));
    }

    private static boolean isLocalCombatTarget(GrindGraphContext context,
                                               Character bot,
                                               Foothold botFoothold,
                                               Monster target) {
        Foothold targetFoothold = botFoothold == null ? null : findGroundFoothold(target.getPosition(), bot);
        return AgentCombatGrindTargetPolicy.isLocalCombatTarget(
                botFoothold,
                targetFoothold,
                context.available(),
                () -> BotNavigationManager.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), target.getPosition()),
                context.startRegionId());
    }

    private static List<AgentScoredGrindTarget> scoreLocalTargets(BotEntry entry,
                                                             Character bot,
                                                             Point botPos,
                                                             Foothold botFoothold,
                                                             List<Monster> candidates) {
        return AgentCombatGrindTargetPolicy.scoreLocalTargets(
                candidates,
                botPos,
                candidate -> grindTargetScore(bot, botPos, botFoothold, candidate),
                candidate -> AgentCombatScoringPolicy.legacyAoeClusterBonus(
                        candidate,
                        candidates,
                        entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                        entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
    }

    private static List<AgentScoredGrindTarget> scoreTargetRegions(BotEntry entry,
                                                              GrindGraphContext context,
                                                              Character bot,
                                                              Point botPos,
                                                              Foothold botFoothold,
                                                              List<Monster> candidates) {
        return AgentCombatGrindTargetPolicy.scoreTargetRegions(
                candidates,
                botPos,
                candidate -> BotNavigationManager.resolveTargetRegionId(
                        context.graph(), context.entry(), context.map(), candidate.getPosition()),
                candidate -> grindTargetScore(bot, botPos, botFoothold, candidate)
                        - AgentCombatScoringPolicy.legacyAoeClusterBonus(
                                candidate,
                                candidates,
                                entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                                entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry)),
                group -> graphPathCost(context.graph(), context.map(), context.startPos(), context.startRegionId(),
                        group.bestMonster().getPosition(), group.regionId(), context.profile()),
                group -> grindRegionOccupancyPenalty(context, bot, group.regionId()),
                UNREACHABLE_GRAPH_COST);
    }

    private static long graphPathCost(BotNavigationGraph graph,
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

        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPathForTargetScore(
                graph, map, startPos, startRegionId, targetRegionId, targetPos);
        List<Long> edgeCosts = new ArrayList<>(path.size());
        for (BotNavigationGraph.Edge edge : path) {
            edgeCosts.add((long) edge.cost);
        }
        return AgentCombatGrindTargetPolicy.graphPathCost(true, false, 0L, edgeCosts, UNREACHABLE_GRAPH_COST);
    }

    private static long grindTargetScore(Character bot, Point botPos, Foothold botFoothold, Monster target) {
        Point targetPos = target.getPosition();
        Foothold targetFoothold = findGroundFoothold(targetPos, bot);

        boolean sameFoothold = botFoothold != null && targetFoothold != null && botFoothold.getId() == targetFoothold.getId();
        return AgentCombatScoringPolicy.localTargetScore(botPos, targetPos, sameFoothold, cfg.ATTACK_RANGE_Y);
    }

    // AoE positioning: target selection (AgentCombatScoringPolicy.legacyAoeClusterBonus) steers the bot toward a cluster, but the
    // fire site still throws the single-target skill the instant one mob is in range — the AoE box
    // at the cluster *edge* catches only that mob, so it ties the denominator and loses on per-hit
    // damage. This returns a sweet-spot Point (the cluster centroid) to walk to when an AoE thrown
    // from there would beat the fire-now plan's DPS by cfg.AOE_REPOSITION_DPS_FACTOR, else null
    // (fire now). Bounded by AOE_REPOSITION_MAX_DISTANCE_X so it never chases scattering mobs.
    static Point aoeRepositionTarget(BotEntry entry, Character bot, Monster primaryTarget, AttackPlan fireNowBest) {
        boolean hasMultiMobAoeSkill = entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry);
        int aoeSkillId = entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry);
        int aoeSkillMobs = entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry);
        // Only when the chosen plan is single-target with room to hit more — skip when the AoE is
        // already the pick or the in-range cluster already maxes the skill's mobCount.
        if (!AgentCombatScoringPolicy.shouldConsiderAoeReposition(
                cfg.AOE_REPOSITION_ENABLED,
                entry != null && bot != null,
                primaryTarget != null,
                hasMultiMobAoeSkill,
                fireNowBest != null,
                fireNowBest != null && fireNowBest.skillId == aoeSkillId,
                fireNowBest == null ? 0 : fireNowBest.targets.size(),
                aoeSkillMobs)) {
            return null;
        }
        Point botPos = bot.getPosition();
        Point tp = primaryTarget.getPosition();
        if (botPos == null || tp == null) {
            return null;
        }
        // Cheap geometry gate first (no scoring): the cluster of live mobs within the AoE radius of
        // the primary. If it holds no more mobs than the fire-now plan already hits, bail.
        List<Monster> cluster = AgentCombatScoringPolicy.legacyClusterMonsters(
                primaryTarget, bot.getMap().getAllMonsters());
        if (cluster.size() <= fireNowBest.targets.size()) {
            return null;
        }
        int centroidX = AgentCombatScoringPolicy.clusterCentroidX(cluster);
        // Rebuild the AoE candidate at the current position (the plan planAttack discards) so we know
        // the actual hitbox shape. The box extends *forward* from the bot (it does not straddle the
        // anchor), so to cover the cluster we shift the box CENTER onto the centroid — not the bot
        // onto the centroid, which would push a forward box past the mobs and catch none. The bot
        // moves by the same shift, bounded by the chase distance.
        AttackPlan aoeNow = planSkillAttack(entry, bot, primaryTarget, aoeSkillId);
        if (aoeNow == null || aoeNow.hitBox == null) {
            return null;
        }
        int shift = AgentCombatScoringPolicy.boundedRepositionShift(
                centroidX, aoeNow.hitBox.getCenterX(), cfg.AOE_REPOSITION_MAX_DISTANCE_X);
        if (AgentCombatScoringPolicy.isWithinRepositionArrival(shift, cfg.AOE_REPOSITION_ARRIVAL_X)) {
            return null; // box already covers the cluster — let the normal flow pick the AoE
        }
        Rectangle shifted = new Rectangle(aoeNow.hitBox);
        shifted.translate(shift, 0);
        Monster sweetPrimary = AgentCombatScoringPolicy.nearestMonster(cluster, centroidX, tp.y);
        if (sweetPrimary == null) {
            return null;
        }
        List<Monster> sweetTargets = AgentCombatTargetSelector.collectTargetsInHitBox(
                sweetPrimary, shifted, aoeSkillMobs, bot.getMap().getAllMonsters());
        if (sweetTargets.size() <= fireNowBest.targets.size()) {
            return null; // repositioning wouldn't actually catch more mobs
        }
        // Geometry is promising — now pay for scoring. scoreAttackPlan is position-independent
        // (target HP + damage profile), so the translated plan scores validly.
        AgentAttackPlanScoringPolicy.AgentAttackPlanScore<AttackPlan> fireNowScore =
                AgentAttackPlanScoringPolicy.scoreAttackPlan(bot, fireNowBest);
        // Preserve kill priority: if the fire-now plan already one-shots a full-HP target, just fire.
        if (fireNowScore.minimumKillsFullHpTargets()) {
            return null;
        }
        AttackPlan sweetPlan = new AttackPlan(aoeNow.skillId, aoeNow.skillLevel, aoeNow.numDamage, shifted,
                sweetTargets, aoeNow.route, aoeNow.display, aoeNow.direction, aoeNow.rangedDirection,
                aoeNow.stance, aoeNow.speed, aoeNow.hitDelayMs, aoeNow.cooldownMs, aoeNow.damageWeaponType);
        AgentAttackPlanScoringPolicy.AgentAttackPlanScore<AttackPlan> sweetScore =
                AgentAttackPlanScoringPolicy.scoreAttackPlan(bot, sweetPlan);
        if (sweetScore.rawDps() >= fireNowScore.rawDps() * cfg.AOE_REPOSITION_DPS_FACTOR) {
            if (cfg.AOE_REPOSITION_DEBUG) {
                double pct = fireNowScore.rawDps() > 0
                        ? sweetScore.rawDps() / fireNowScore.rawDps() * 100.0d
                        : 0.0d;
                log.info("AoE reposition[{}]: stepping {}px {} to hit {} mobs (vs {}) with {} for {}% DPS ({} vs {} dps)",
                        bot.getName(), Math.abs(shift), shift < 0 ? "left" : "right",
                        sweetTargets.size(), fireNowBest.targets.size(),
                        AgentCombatDialogueReporter.combatSkillLabel(aoeSkillId),
                        Math.round(pct), Math.round(sweetScore.rawDps()), Math.round(fireNowScore.rawDps()));
            }
            return new Point(botPos.x + shift, botPos.y);
        }
        return null;
    }

    private static long grindRegionOccupancyPenalty(GrindGraphContext context, Character bot, int targetRegionId) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(context.entry());
        if (!context.available() || owner == null || bot == null || targetRegionId < 0) {
            return 0L;
        }

        int occupiedCount = 0;
        for (BotEntry sibling : BotManager.getInstance().getBotEntries(owner.getId())) {
            if (sibling == context.entry() || sibling == null || !AgentBotModeStateRuntime.grinding(sibling)) {
                continue;
            }
            Character siblingBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
            if (siblingBot == null) {
                continue;
            }
            boolean sameMap = siblingBot.getMap() == context.map();
            boolean alive = siblingBot.getHp() > 0;
            boolean hasPosition = siblingBot.getPosition() != null;
            if (!AgentCombatGrindTargetPolicy.shouldInspectRegionOccupant(
                    sibling == context.entry(),
                    AgentBotModeStateRuntime.grinding(sibling),
                    sameMap,
                    alive,
                    hasPosition)) {
                continue;
            }

            int occupiedRegionId = BotNavigationManager.resolveCurrentRegionId(
                    context.graph(), sibling, context.map(), siblingBot.getPosition());
            if (AgentCombatGrindTargetPolicy.shouldCountRegionOccupant(occupiedRegionId, targetRegionId)) {
                occupiedCount++;
            }
        }

        return AgentCombatGrindTargetPolicy.occupancyPenalty(occupiedCount,
                cfg.GRIND_REGION_OCCUPANCY_PENALTY, cfg.GRIND_REGION_OCCUPANCY_PENALTY_CAP);
    }

    private static Foothold findGroundFoothold(Point position, Character bot) {
        if (position == null || bot == null || bot.getMap() == null) {
            return null;
        }

        return BotPhysicsEngine.findGroundFoothold(bot.getMap(), position);
    }

    private record GrindGraphContext(BotEntry entry,
                                     MapleMap map,
                                     BotNavigationGraph graph,
                                     AgentMovementProfile profile,
                                     Point startPos,
                                     int startRegionId) {
        static GrindGraphContext resolve(BotEntry entry, Character bot, Point botPos) {
            if (entry == null || bot == null || bot.getMap() == null || bot.getMap().getFootholds() == null) {
                return unavailable(entry, bot, botPos);
            }

            AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            BotNavigationGraph graph = BotNavigationGraphProvider.peekGraph(bot.getMap(), profile);
            if (graph == null) {
                BotNavigationGraphProvider.warmGraphAsync(bot.getMap(), profile);
                graph = BotNavigationGraphProvider.peekClosestGraph(bot.getMap(), profile);
            }
            if (graph == null) {
                return unavailable(entry, bot, botPos);
            }

            int startRegionId = BotNavigationManager.resolveCurrentRegionId(graph, entry, bot.getMap(), botPos);
            if (startRegionId < 0) {
                return unavailable(entry, bot, botPos);
            }
            return new GrindGraphContext(entry, bot.getMap(), graph, profile, new Point(botPos), startRegionId);
        }

        private static GrindGraphContext unavailable(BotEntry entry, Character bot, Point botPos) {
            MapleMap map = bot == null ? null : bot.getMap();
            AgentMovementProfile profile = AgentBotMovementStateRuntime.movementProfileOrCharacter(entry, bot);
            Point startPos = botPos == null ? null : new Point(botPos);
            return new GrindGraphContext(entry, map, null, profile, startPos, -1);
        }

        boolean available() {
            return graph != null && map != null && startPos != null && startRegionId >= 0 && entry != null;
        }
    }

    public static String describeDebugStats(BotEntry entry, Character bot) {
        return AgentBotCombatReportRuntime.debugStatsReport(entry, bot);
    }

}



