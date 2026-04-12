package server.bots;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import constants.skills.Archer;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.Cleric;
import constants.skills.Corsair;
import constants.skills.DawnWarrior;
import constants.skills.Fighter;
import constants.skills.GM;
import constants.skills.Marksman;
import constants.skills.NightWalker;
import constants.skills.Priest;
import constants.skills.Rogue;
import constants.skills.Spearman;
import constants.skills.SuperGM;
import constants.skills.ThunderBreaker;
import constants.skills.WindArcher;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.StatEffect;
import server.bots.combat.BotAttackDataProvider;
import server.combat.CombatFormulaProvider;
import server.bots.combat.BotDefenseDataProvider;
import server.bots.combat.BotMobHitboxProvider;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

class BotCombatManager {
    private static final long UNREACHABLE_GRAPH_COST = Long.MAX_VALUE / 4;

    enum AttackRoute {
        CLOSE,
        RANGED,
        MAGIC
    }

    static final class AttackPlan {
        final int skillId;
        final int skillLevel;
        final int numDamage;
        final Rectangle hitBox;
        final List<Monster> targets;
        final AttackRoute route;
        final int display;
        final int direction;
        final int rangedDirection;
        final int stance;
        final int speed;
        final int hitDelayMs;
        final int cooldownMs;

        AttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                   AttackRoute route, int display, int direction, int rangedDirection, int stance, int speed,
                   int hitDelayMs, int cooldownMs) {
            this.skillId = skillId;
            this.skillLevel = skillLevel;
            this.numDamage = numDamage;
            this.hitBox = hitBox;
            this.targets = targets;
            this.route = route;
            this.display = display;
            this.direction = direction;
            this.rangedDirection = rangedDirection;
            this.stance = stance;
            this.speed = speed;
            this.hitDelayMs = hitDelayMs;
            this.cooldownMs = cooldownMs;
        }

        boolean hasHitBox() {
            return hitBox != null;
        }

        Monster primaryTarget() {
            return targets.get(0);
        }

        boolean isCloseRangeRoute() {
            return route == AttackRoute.CLOSE;
        }
    }

    static class Config {
        // Physics (combat use only)
        // OpenStory Player::damage sets hspeed = +/-1.5 and vforce -= 3.5 on mob knockback.
        public float KNOCKBACK_HSPEED = 1.5f;
        public float KNOCKBACK_VFORCE = 3.5f;

        // Basic attack fallback when weapon data cannot produce a real normal-attack hit box.
        public int   ATTACK_RANGE_X  = 80;
        public int   ATTACK_RANGE_Y  = 50;
        public int   ATTACK_DOWN_MAX = 20;
        public int   ATTACK_JUMP_Y   = 130;
        public int   ATTACK_JUMP_X_EXTRA = 60;
        public int   RANGED_DEGENERATE_RANGE_X = 140;
        public int   RANGED_DEGENERATE_RANGE_Y = 70;
        public int   RANGED_RETREAT_THRESHOLD_X = 100;
        public int   RANGED_RETREAT_DISTANCE_X = 120;

        // Ammo
        public int   AMMO_LOW_WARN = 200;

        // Grind / AoE
        public int   GRIND_SEEK_RANGE  = 800;
        public int   GRIND_RETARGET_INTERVAL_MS = 400;
        public int   AOE_MOB_THRESHOLD = 2;

        // Mob damage
        public int   MOB_TOUCH_SWEEP_HEIGHT = 50;
        public int   MOB_HIT_COOLDOWN_MS = 1500;
        public long  BOT_DEAD_MS      = 30_000L;

        // Support
        public int   SUPPORT_RANGE = 400;
        public int   SUPPORT_VERTICAL_RANGE = 220;
        public int   SUPPORT_REBUFF_CD_MS = 3_000;
        public int   SUPPORT_HEAL_CD_MS = 2_000;
        public float SUPPORT_HEAL_RATIO = 0.75f;
        public int   SUPPORT_HEAL_MISSING_HP = 250;
    }

    static Config cfg = new Config();
    // Journey client CharStats::get_range() returns Rectangle(-projectilerange, -5, -50, 50).
    private static final int CLIENT_PROJECTILE_BASE_RANGE = 400;
    private static final int CLIENT_PROJECTILE_NEAR_INSET = 5;
    private static final int CLIENT_PROJECTILE_TOP = 50;
    private static final int CLIENT_PROJECTILE_BOTTOM = 50;
    private static final List<Integer> PASSIVE_PROJECTILE_RANGE_SKILL_IDS = List.of(
            Archer.EYE_OF_AMAZON,
            4000001,
            Rogue.KEEN_EYES,
            WindArcher.EYE_OF_AMAZON,
            NightWalker.KEEN_EYES
    );
    private static final Set<Integer> PARTY_SUPPORT_SKILL_IDS = Set.of(
            Assassin.HASTE,
            Bandit.HASTE,
            NightWalker.HASTE,
            Fighter.RAGE,
            DawnWarrior.RAGE,
            Cleric.BLESS,
            Priest.HOLY_SYMBOL,
            Spearman.HYPER_BODY,
            Buccaneer.PIRATES_RAGE,
            Buccaneer.SPEED_INFUSION,
            Corsair.SPEED_INFUSION,
            ThunderBreaker.SPEED_INFUSION,
            Bowmaster.SHARP_EYES,
            Marksman.SHARP_EYES,
            GM.HASTE,
            GM.BLESS,
            GM.HYPER_BODY,
            SuperGM.HASTE,
            SuperGM.HOLY_SYMBOL,
            SuperGM.HYPER_BODY
    );

    private static final List<String> DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");
    private static final List<String> AMMO_LOW_MSGS = List.of(
            "running low on ammo",
            "ammo getting low",
            "not much ammo left",
            "gonna need more ammo soon");
    private static final List<String> AMMO_OUT_MSGS = List.of(
            "out of ammo! heading back",
            "no ammo left, coming to you",
            "need ammo!! walking back",
            "im out of ammo, heading to you");
    private static final List<String> MP_POTS_OUT_MSGS = List.of(
            "out of MP pots! heading back",
            "no MP pots left, coming to you",
            "need MP pots!! walking back",
            "im out of MP pots, heading to you");

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    static void tickMobDamage(BotEntry entry, Character bot) {
        Point botPos = bot.getPosition();
        try {
            if (entry.mobHitCooldownMs > 0) {
                entry.mobHitCooldownMs = BotMovementManager.tickDown(entry.mobHitCooldownMs);
                return;
            }
            if (bot.getHp() <= 0) return;

            for (Monster mob : bot.getMap().getAllMonsters()) {
                if (!mob.isAlive()) continue;
                if (isMobTouchingBot(entry, bot, mob)) {
                    applyMobHit(entry, bot, mob);
                    return;
                }
            }
        } finally {
            rememberMobTouchCheck(entry, bot, botPos);
        }
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Uses the bot's shared character WDEF cache instead of ignoring defense entirely.
     */
    static void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        Config cc = BotCombatManager.cfg;
        int dmg = rollPhysicalMobDamage(bot, mob);
        Point botPos = bot.getPosition();
        MobHitKnockback knockback = resolveMobHitKnockback(botPos, mob.getPosition());

        if (dmg <= 0) {
            bot.getMap().broadcastMessage(bot,
                    PacketCreator.damagePlayer(-1, mob.getId(), bot.getId(), 0, 0,
                            knockback.direction(), false, 0, false, 0, 0, 0), false);
            entry.mobHitCooldownMs = BotMovementManager.delayAfterCurrentTick(cc.MOB_HIT_COOLDOWN_MS);
            return;
        }

        bot.addMPHPAndTriggerAutopot(-dmg, 0);

        // OpenStory touch attacks use the mob's position as attack origin:
        // direction 0 means the hit came from the right and knocks the player left.
        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(-1, mob.getId(), bot.getId(), dmg, 0,
                        knockback.direction(), false, 0, false, 0, 0, 0), false);

        entry.mobHitCooldownMs = BotMovementManager.delayAfterCurrentTick(cc.MOB_HIT_COOLDOWN_MS);

        if (bot.getHp() <= 0) {
            enterDeadState(entry, bot, true);
            return;
        }

        if (!shouldApplyMobKnockback(entry, bot)) {
            return;
        }

        clearActionState(entry);
        if (entry.inAir) {
            BotPhysicsEngine.applyAirKnockback(entry, bot, knockback.airVelX());
        } else {
            BotPhysicsEngine.beginKnockback(entry, bot, botPos, -scaledOpenStoryStep(cfg.KNOCKBACK_VFORCE), knockback.airVelX());
        }
        BotMovementManager.broadcastMovement(entry);
    }

    private static int rollPhysicalMobDamage(Character bot, Monster mob) {
        return BotDefenseDataProvider.getInstance().rollPhysicalTouchDamage(bot, mob);
    }

    private static boolean shouldApplyMobKnockback(BotEntry entry, Character bot) {
        if (entry.climbing || bot.getHp() <= 0) {
            return false;
        }

        Integer stancePercent = bot.getBuffedValue(BuffStat.STANCE);
        if (stancePercent == null || stancePercent <= 0) {
            return true;
        }

        float stanceChance = Math.max(0f, Math.min(1f, stancePercent / 100f));
        return ThreadLocalRandom.current().nextFloat() > stanceChance;
    }

    private static MobHitKnockback resolveMobHitKnockback(Point botPos, Point attackOrigin) {
        boolean attackFromRight = attackOrigin.x > botPos.x;
        int direction = attackFromRight ? 0 : 1;
        int airVelX = Math.round((attackFromRight ? -1f : 1f) * scaledOpenStoryStep(cfg.KNOCKBACK_HSPEED));
        return new MobHitKnockback(direction, airVelX);
    }

    private static float scaledOpenStoryStep(float openStoryStepValue) {
        return openStoryStepValue * (BotMovementManager.cfg.TICK_MS / 8.0f);
    }

    static void enterDeadState(BotEntry entry, Character bot, boolean announceDeath) {
        clearActionState(entry);
        BotPhysicsEngine.markDead(entry, bot);
        BotMovementManager.broadcastMovement(entry);
        entry.deadUntil = System.currentTimeMillis() + cfg.BOT_DEAD_MS;
        if (announceDeath) {
            BotManager.getInstance().botSay(bot, BotManager.randomReply(DEATH_REPLIES));
        }
    }

    private static void clearActionState(BotEntry entry) {
        entry.grindTarget = null;
        entry.attackCooldownMs = 0;
        BotMovementManager.clearNavigationState(entry);
        entry.movementBroadcastValid = false;
    }

    private record MobHitKnockback(int direction, int airVelX) {
    }

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        if (entry.cachedSkillJob == bot.getJob().getId()
                && entry.cachedSkillLevel == bot.getLevel()) {
            return;
        }

        entry.cachedSkillJob = bot.getJob().getId();
        entry.cachedSkillLevel = bot.getLevel();
        entry.attackSkillId = 0;
        entry.aoeSkillId = 0;
        entry.aoeSkillMobs = 1;
        entry.healSkillId = 0;
        entry.buffSkillIds.clear();

        int bestAtkHits = 0;
        int bestAtkPriority = Integer.MIN_VALUE;
        int bestAtkDamage = Integer.MIN_VALUE;
        int bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            int atk = effectiveHitCount(fx);
            int mobs = fx.getMobCount();
            int dur = fx.getDuration();

            if (isHealSkill(skill.getId())) {
                entry.healSkillId = skill.getId();
            }

            if (fx.getMpCon() > 0) {  // mpCon > 0 identifies real attack/active skills; attackCount defaults to 1 for all skills
                if (mobs >= 2) {
                    int score = mobs * atk;
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        entry.aoeSkillId = skill.getId();
                        entry.aoeSkillMobs = mobs;
                    }
                } else if (shouldUseAsBestSingleTargetSkill(bot, skill, fx, atk,
                        bestAtkHits, bestAtkPriority, bestAtkDamage, entry.attackSkillId)) {
                    bestAtkHits = atk;
                    bestAtkPriority = singleTargetSkillPriority(bot, skill);
                    bestAtkDamage = fx.getDamage();
                    entry.attackSkillId = skill.getId();
                }
                continue;
            }

            if (dur <= 0) {
                continue;
            }

            entry.buffSkillIds.add(skill.getId());
            entry.nextBuffAt.putIfAbsent(skill.getId(), 0L);
        }
    }

    static void tickBuffs(BotEntry entry, Character bot) {
        if (entry.attackCooldownMs > 0) return;
        if (!entry.following && !entry.grinding) return;
        if (entry.buffSkillIds.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (trySupportBuff(entry, bot, now)) {
            return;
        }
        if (bot.getMap().getAllMonsters().stream().noneMatch(Monster::isAlive)) return;

        for (int skillId : entry.buffSkillIds) {
            if (now < entry.nextBuffAt.getOrDefault(skillId, 0L)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            if (castSupportSkill(entry, bot, skill, fx, now)) {
                return;
            }
        }
    }

    private static boolean shouldUseAsBestSingleTargetSkill(Character bot, Skill skill, StatEffect effect,
                                                            int attackCount, int bestAttackCount,
                                                            int bestPriority, int bestDamage,
                                                            int currentBestSkillId) {
        int priority = singleTargetSkillPriority(bot, skill);
        if (priority != bestPriority) {
            return priority > bestPriority;
        }

        int damage = effect != null ? effect.getDamage() : 0;
        int score = damage * attackCount;
        int bestScore = bestDamage * bestAttackCount;
        if (score != bestScore) {
            return score > bestScore;
        }

        return currentBestSkillId == 0 || skill.getId() < currentBestSkillId;
    }

    private static int singleTargetSkillPriority(Character bot, Skill skill) {
        if (skill == null) {
            return Integer.MIN_VALUE;
        }
        if (skill.isBeginnerSkill()) {
            return 0;
        }
        return GameConstants.isInJobTree(skill.getId(), bot.getJob().getId()) ? 2 : 1;
    }

    static void tickSupportHealing(BotEntry entry, Character bot) {
        if (entry.attackCooldownMs > 0) return;
        if (!entry.supportHealsEnabled) return;
        if (!entry.following && !entry.grinding) return;
        if (entry.healSkillId == 0 || bot.skillIsCooling(entry.healSkillId)) return;

        long now = System.currentTimeMillis();
        if (now < entry.nextSupportHealAt) return;
        if (!hasNearbyPartyMemberNeedingHeal(bot)) return;

        Skill skill = SkillFactory.getSkill(entry.healSkillId);
        int lvl = bot.getSkillLevel(skill);
        if (lvl <= 0) return;

        StatEffect fx = skill.getEffect(lvl);
        if (!fx.canPaySkillCost(bot)) {
            return;
        }
        if (!fx.applyTo(bot)) {
            return;
        }

        entry.nextSupportHealAt = now + cfg.SUPPORT_HEAL_CD_MS;
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData =
                BotAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, lvl,
                BotAttackExecutionProvider.getEquippedWeaponType(bot));
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs, skillTiming.cooldownMs());
        if (fx.getCooldown() > 0) {
            bot.addCooldown(entry.healSkillId, now, fx.getCooldown() * 1000L);
        }
    }

    static Monster findGrindTarget(Character bot) {
        return findGrindTarget(null, bot);
    }

    /** Returns a random monster from the most convenient 3 reachable targets, so multiple bots spread. */
    static Monster findGrindTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Foothold botFoothold = findGroundFoothold(botPos, bot);
            List<Monster> candidates = aliveMonstersInRange(bot, botPos, rangeSq);
            if (candidates.isEmpty()) return null;

            List<ScoredGrindTarget> scoredTargets = scoreGrindTargets(entry, bot, botPos, botFoothold, candidates);
            if (scoredTargets.isEmpty()) {
                return null;
            }

            scoredTargets.sort(Comparator
                    .comparingLong(ScoredGrindTarget::graphCost)
                    .thenComparingLong(ScoredGrindTarget::localScore)
                    .thenComparingDouble(ScoredGrindTarget::distanceSq));
            List<ScoredGrindTarget> reachableTargets = scoredTargets.stream()
                    .filter(target -> target.graphCost() < UNREACHABLE_GRAPH_COST)
                    .toList();
            List<ScoredGrindTarget> selectable = reachableTargets.isEmpty() ? scoredTargets : reachableTargets;
            if (selectable.getFirst().graphCost() >= UNREACHABLE_GRAPH_COST) {
                return null;
            }

            return selectable.get(ThreadLocalRandom.current().nextInt(Math.min(3, selectable.size()))).monster();
        } finally {
            BotPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    /** Follow mode should only attack local mobs; it should not run pathfinding or chase across the map. */
    static Monster findFollowAttackTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double range = Math.max(CLIENT_PROJECTILE_BASE_RANGE + passiveProjectileRangeBonus(bot),
                    BotCombatManager.cfg.ATTACK_RANGE_X + BotCombatManager.cfg.ATTACK_JUMP_X_EXTRA);
            List<Monster> candidates = aliveMonstersInRange(bot, botPos, range * range);
            if (candidates.isEmpty()) {
                return null;
            }

            Foothold botFoothold = findGroundFoothold(botPos, bot);
            GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
            List<ScoredGrindTarget> localTargets = new ArrayList<>();
            for (Monster candidate : candidates) {
                if (!isLocalCombatTarget(graphContext, bot, botFoothold, candidate)
                        && !isImmediateProjectileTarget(entry, bot, candidate)) {
                    continue;
                }
                long localScore = grindTargetScore(bot, botPos, botFoothold, candidate);
                localTargets.add(new ScoredGrindTarget(candidate, localScore, localScore,
                        candidate.getPosition().distanceSq(botPos)));
            }
            return pickFromBestTargets(localTargets);
        } finally {
            BotPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
    }

    static boolean isReachableGrindTarget(BotEntry entry, Character bot, Monster target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (entry == null || bot == null) {
            return true;
        }

        GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, bot.getPosition());
        if (isImmediateProjectileTarget(entry, bot, target)) {
            return true;
        }
        return !graphContext.available() || graphTargetCost(graphContext, target) < UNREACHABLE_GRAPH_COST;
    }

    static AttackPlan planAttack(BotEntry entry, Character bot, Monster target) {
        long startedAt = System.nanoTime();
        try {
            AttackPlan aoeAttack = planAoeAttack(entry, bot, target);
            if (aoeAttack != null) {
                return aoeAttack;
            }

            AttackPlan skillAttack = planSingleTargetSkill(entry, bot, target);
            if (skillAttack != null) {
                return skillAttack;
            }

            BotAttackExecutionProvider.BasicAttackData basicAttackData = buildBasicAttackData(bot, target);
            Monster effective = resolveEffectivePrimary(bot, target, basicAttackData.hitBox());
            if (effective != target) {
                basicAttackData = buildBasicAttackData(bot, effective);
            }
            return new AttackPlan(0, 0, 1, basicAttackData.hitBox(), List.of(effective), basicAttackData.route(),
                    basicAttackData.display(), basicAttackData.direction(), basicAttackData.rangedDirection(), basicAttackData.stance(),
                    basicAttackData.speed(), basicAttackData.hitDelayMs(), basicAttackData.cooldownMs());
        } finally {
            BotPerformanceMonitor.record("combat-plan", System.nanoTime() - startedAt);
        }
    }

    static boolean isTargetInAttackRange(AttackPlan attackPlan, Character bot, Monster target) {
        if (attackPlan.hasHitBox()) {
            return doesHitBoxIntersectMonster(attackPlan.hitBox, target);
        }
        return isBasicAttackInRange(bot.getPosition(), target.getPosition());
    }

    static boolean isTargetJumpable(BotMovementProfile movementProfile, boolean closeRangeRoute, Point botPos, Point targetPos) {
        if (!closeRangeRoute || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        if (dx > BotCombatManager.cfg.ATTACK_RANGE_X + BotCombatManager.cfg.ATTACK_JUMP_X_EXTRA) {
            return false;
        }

        int dy = botPos.y - targetPos.y;
        int maxJumpHeight = Math.max(BotCombatManager.cfg.ATTACK_JUMP_Y,
                (int) Math.ceil(BotPhysicsEngine.calculateMaxJumpHeight(movementProfile)));
        return dy > BotCombatManager.cfg.ATTACK_RANGE_Y && dy <= maxJumpHeight;
    }

    static boolean isTargetJumpable(boolean closeRangeRoute, Point botPos, Point targetPos) {
        return isTargetJumpable(BotMovementProfile.base(), closeRangeRoute, botPos, targetPos);
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        if (entry.attackCooldownMs > 0) {
            return;
        }
        if (entry.noAmmo) {
            return;
        }
        if (attackPlan.skillId != 0 && !canUseSkill(bot, attackPlan.skillId, attackPlan.skillLevel)) {
            return;
        }

        int numAttacked = attackPlan.targets.size();
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = attackPlan.skillId;
        attack.skilllevel = attackPlan.skillLevel;
        attack.numDamage = attackPlan.numDamage;
        attack.numAttacked = numAttacked;
        attack.numAttackedAndDamage = (numAttacked << 4) | attackPlan.numDamage;
        attack.speed = attackPlan.speed;
        attack.stance = attackPlan.stance; // Historical server name: packet byte 3.
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction; // Historical server name: packet byte 2.
        attack.rangedirection = attackPlan.rangedDirection; // Extra ranged byte after speed.
        attack.ranged = attackPlan.route == AttackRoute.RANGED;
        CombatFormulaProvider.DamageProfile damageProfile = CombatFormulaProvider.getInstance().resolveDamageProfile(
                bot, attackPlan.skillId, attackPlan.skillLevel,
                attackPlan.route == AttackRoute.MAGIC);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(bot, target, attackPlan.numDamage,
                            damageProfile, attackPlan.hitDelayMs));
        }

        BotAttackExecutionProvider.applyAttackRoute(attackPlan.route, attack, bot);
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs, attackPlan.cooldownMs);
    }

    static void tickActionLock(BotEntry entry) {
        if (entry.attackCooldownMs <= 0) {
            return;
        }
        entry.attackCooldownMs = BotMovementManager.tickDown(entry.attackCooldownMs);
    }

    private static AttackPlan planAoeAttack(BotEntry entry, Character bot, Monster primaryTarget) {
        if (entry.aoeSkillId == 0 || bot.skillIsCooling(entry.aoeSkillId)) {
            return null;
        }

        Skill skill = SkillFactory.getSkill(entry.aoeSkillId);
        int skillLevel = bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return null;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        if (!effect.canPaySkillCost(bot)) {
            return null;
        }
        AttackRoute route = BotAttackExecutionProvider.determineSkillRoute(bot, entry.aoeSkillId);
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget, route);
        if (hitBox == null) {
            return null;
        }

        primaryTarget = resolveEffectivePrimary(bot, primaryTarget, hitBox);
        List<Monster> targets = collectTargetsInHitBox(bot, primaryTarget, hitBox, Math.max(1, effect.getMobCount()));
        if (targets.size() < BotCombatManager.cfg.AOE_MOB_THRESHOLD) {
            return null;
        }

        int attackCount = Math.max(1, effect.getAttackCount());
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!BotAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BotAttackDataProvider.AttackAnimationSpec attackSpec = BotAttackDataProvider.getInstance().getBasicAttackSpec(weaponType);
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        BotAttackExecutionProvider.CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? BotAttackExecutionProvider.mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.bodyActionId()
                : BotAttackExecutionProvider.bodyActionId(action, fallbackAction);
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.aoeSkillId, skillLevel, attackCount, hitBox, targets,
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction,
                route == AttackRoute.CLOSE ? closeRangePacketFields.facingMask() : facingLeft ? -128 : 0,
                fallbackAttackData.speed(), skillTiming.hitDelayMs(), skillTiming.cooldownMs());
    }

    private static AttackPlan planSingleTargetSkill(BotEntry entry, Character bot, Monster primaryTarget) {
        if (entry.attackSkillId == 0 || bot.skillIsCooling(entry.attackSkillId)) {
            return null;
        }

        Skill skill = SkillFactory.getSkill(entry.attackSkillId);
        int skillLevel = bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return null;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        if (!effect.canPaySkillCost(bot)) {
            return null;
        }
        AttackRoute route = BotAttackExecutionProvider.determineSkillRoute(bot, entry.attackSkillId);
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget, route);
        if (hitBox == null) {
            return null;
        }

        primaryTarget = resolveEffectivePrimary(bot, primaryTarget, hitBox);
        if (!doesHitBoxIntersectMonster(hitBox, primaryTarget)) {
            return null;
        }

        int attackCount = effectiveHitCount(effect);
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!BotAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BotAttackDataProvider.AttackAnimationSpec attackSpec = BotAttackDataProvider.getInstance().getBasicAttackSpec(weaponType);
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        BotAttackExecutionProvider.CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? BotAttackExecutionProvider.mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.bodyActionId()
                : BotAttackExecutionProvider.bodyActionId(action, fallbackAction, weaponType);
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.attackSkillId, skillLevel, attackCount, hitBox, List.of(primaryTarget),
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction,
                route == AttackRoute.CLOSE ? closeRangePacketFields.facingMask() : facingLeft ? -128 : 0,
                fallbackAttackData.speed(), skillTiming.hitDelayMs(), skillTiming.cooldownMs());
    }

    private static Rectangle calculateSkillHitBox(StatEffect effect, Character bot, Monster primaryTarget, AttackRoute route) {
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        if (effect.hasBoundingBox()) {
            return effect.calculateBoundingBox(bot.getPosition(), facingLeft);
        }

        return fallbackSkillHitBox(effect, bot, facingLeft, route);
    }

    static Rectangle fallbackCloseRangeSkillHitBox(StatEffect effect, Character bot, boolean facingLeft) {
        if (effect == null || bot == null
                || BotAttackExecutionProvider.determineBasicAttackRoute(bot) != AttackRoute.CLOSE) {
            return null;
        }

        Point origin = bot.getPosition();
        int horizontalRange = Math.max(cfg.ATTACK_RANGE_X, effect.getRange());
        int top = origin.y - cfg.ATTACK_RANGE_Y;
        int height = cfg.ATTACK_RANGE_Y + cfg.ATTACK_DOWN_MAX;
        int left = facingLeft ? origin.x - horizontalRange : origin.x;
        return new Rectangle(left, top, horizontalRange, height);
    }

    static Rectangle fallbackSkillHitBox(StatEffect effect, Character bot, boolean facingLeft, AttackRoute route) {
        if (route == AttackRoute.CLOSE) {
            return fallbackCloseRangeSkillHitBox(effect, bot, facingLeft);
        }
        if (effect == null || bot == null) {
            return null;
        }

        return clientProjectileHitBox(bot, facingLeft, projectileRangeScale(effect));
    }

    static Rectangle clientProjectileHitBox(Character bot, boolean facingLeft, float horizontalScale) {
        if (bot == null || bot.getPosition() == null) {
            return null;
        }

        Point origin = bot.getPosition();
        int projectileRange = CLIENT_PROJECTILE_BASE_RANGE + passiveProjectileRangeBonus(bot);
        WeaponType wt = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (wt == WeaponType.CLAW) {
            projectileRange -= 150;
        }
        int farEdge = Math.max(CLIENT_PROJECTILE_NEAR_INSET, Math.round(projectileRange * Math.max(0f, horizontalScale)));
        int left = facingLeft ? origin.x - farEdge : origin.x + CLIENT_PROJECTILE_NEAR_INSET;
        int right = facingLeft ? origin.x - CLIENT_PROJECTILE_NEAR_INSET : origin.x + farEdge;
        return new Rectangle(left, origin.y - CLIENT_PROJECTILE_TOP, right - left,
                CLIENT_PROJECTILE_TOP + CLIENT_PROJECTILE_BOTTOM);
    }

    static float projectileRangeScale(StatEffect effect) {
        return effect != null && effect.getRange() > 0 ? effect.getRange() / 100.0f : 1.0f;
    }

    static int passiveProjectileRangeBonus(Character bot) {
        if (bot == null) {
            return 0;
        }

        int bonus = 0;
        for (int skillId : PASSIVE_PROJECTILE_RANGE_SKILL_IDS) {
            Skill skill = resolveLearnedSkill(bot, skillId);
            if (skill == null) {
                continue;
            }

            int level = bot.getSkillLevel(skill);
            if (level <= 0) {
                continue;
            }

            bonus += Math.max(0, skill.getEffect(level).getRange());
        }
        return bonus;
    }

    private static Skill resolveLearnedSkill(Character bot, int skillId) {
        Map<Skill, Character.SkillEntry> skills = bot.getSkills();
        if (skills != null) {
            for (Skill learned : skills.keySet()) {
                if (learned != null && learned.getId() == skillId) {
                    return learned;
                }
            }
        }

        return SkillFactory.getSkill(skillId);
    }

    private static List<Monster> collectTargetsInHitBox(Character bot, Monster primaryTarget, Rectangle hitBox, int maxTargets) {
        if (!doesHitBoxIntersectMonster(hitBox, primaryTarget)) {
            return List.of();
        }

        List<Monster> targets = new ArrayList<>();
        targets.add(primaryTarget);
        if (maxTargets <= 1) {
            return targets;
        }

        List<Monster> secondaryTargets = new ArrayList<>();
        for (Monster monster : bot.getMap().getAllMonsters()) {
            if (!monster.isAlive() || monster.getObjectId() == primaryTarget.getObjectId()) {
                continue;
            }
            if (!doesHitBoxIntersectMonster(hitBox, monster)) {
                continue;
            }
            secondaryTargets.add(monster);
        }

        secondaryTargets.sort(Comparator.comparingDouble(monster -> monster.getPosition().distanceSq(primaryTarget.getPosition())));
        for (Monster monster : secondaryTargets) {
            targets.add(monster);
            if (targets.size() >= maxTargets) {
                break;
            }
        }
        return targets;
    }

    private static boolean isBasicAttackInRange(Point botPos, Point targetPos) {
        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = botPos.y - targetPos.y;
        boolean inHRange = dx <= BotCombatManager.cfg.ATTACK_RANGE_X;
        boolean inVRange = dy >= -BotCombatManager.cfg.ATTACK_DOWN_MAX && dy <= BotCombatManager.cfg.ATTACK_RANGE_Y;
        return inHRange && inVRange;
    }

    /**
     * Returns the number of damage lines a skill fires per target.
     * Uses the larger of attackCount and bulletCount from skill data —
     * claw skills like Lucky Seven store their projectile count in bulletCount.
     */
    private static int effectiveHitCount(StatEffect effect) {
        return Math.max(1, Math.max(effect.getAttackCount(), effect.getBulletCount()));
    }

    private static List<ScoredGrindTarget> scoreGrindTargets(BotEntry entry,
                                                             Character bot,
                                                             Point botPos,
                                                             Foothold botFoothold,
                                                             List<Monster> candidates) {
        GrindGraphContext graphContext = GrindGraphContext.resolve(entry, bot, botPos);
        List<ScoredGrindTarget> currentRegionTargets = new ArrayList<>();
        for (Monster candidate : candidates) {
            if (!isLocalCombatTarget(graphContext, bot, botFoothold, candidate)
                    && !isImmediateProjectileTarget(entry, bot, candidate)) {
                continue;
            }
            long localScore = grindTargetScore(bot, botPos, botFoothold, candidate);
            currentRegionTargets.add(new ScoredGrindTarget(candidate, localScore, localScore,
                    candidate.getPosition().distanceSq(botPos)));
        }
        if (!currentRegionTargets.isEmpty()) {
            return currentRegionTargets;
        }

        return graphContext.available()
                ? scoreTargetRegions(graphContext, bot, botPos, botFoothold, candidates)
                : scoreLocalTargets(bot, botPos, botFoothold, candidates);
    }

    private static List<Monster> aliveMonstersInRange(Character bot, Point botPos, double rangeSq) {
        List<Monster> candidates = new ArrayList<>();
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (m.isAlive() && m.getPosition().distanceSq(botPos) <= rangeSq) {
                candidates.add(m);
            }
        }
        return candidates;
    }

    private static boolean isLocalCombatTarget(GrindGraphContext context,
                                               Character bot,
                                               Foothold botFoothold,
                                               Monster target) {
        if (botFoothold != null) {
            Foothold targetFoothold = findGroundFoothold(target.getPosition(), bot);
            if (targetFoothold != null && targetFoothold.getId() == botFoothold.getId()) {
                return true;
            }
        }
        if (!context.available()) {
            return false;
        }

        int targetRegionId = BotNavigationManager.resolveTargetRegionId(
                context.graph(), context.entry(), context.map(), target.getPosition());
        return targetRegionId >= 0 && targetRegionId == context.startRegionId();
    }

    private static boolean isImmediateProjectileTarget(BotEntry entry, Character bot, Monster target) {
        if (entry == null || entry.noAmmo || bot == null || target == null || !target.isAlive()) {
            return false;
        }

        Point botPos = bot.getPosition();
        Point targetPos = target.getPosition();
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (BotAttackExecutionProvider.determineBasicWeaponRoute(weaponType) == AttackRoute.RANGED
                && !BotAttackExecutionProvider.shouldDegenerateRangedAttack(weaponType, botPos, targetPos)) {
            Rectangle hitBox = clientProjectileHitBox(bot, targetPos.x < botPos.x, 1.0f);
            if (doesHitBoxIntersectMonster(hitBox, target)) {
                return true;
            }
        }

        return isImmediateProjectileSkillTarget(entry, bot, target);
    }

    private static boolean isImmediateProjectileSkillTarget(BotEntry entry, Character bot, Monster target) {
        if (entry.attackSkillId == 0 || bot.skillIsCooling(entry.attackSkillId)) {
            return false;
        }

        Skill skill = SkillFactory.getSkill(entry.attackSkillId);
        int skillLevel = skill == null ? 0 : bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return false;
        }

        StatEffect effect = skill.getEffect(skillLevel);
        if (effect == null || !effect.canPaySkillCost(bot)) {
            return false;
        }

        AttackRoute route = BotAttackExecutionProvider.determineSkillRoute(bot, entry.attackSkillId);
        if (route != AttackRoute.RANGED && route != AttackRoute.MAGIC) {
            return false;
        }

        Rectangle hitBox = calculateSkillHitBox(effect, bot, target, route);
        if (hitBox == null || !doesHitBoxIntersectMonster(hitBox, target)) {
            return false;
        }

        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        return BotAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), target.getPosition());
    }

    private static List<ScoredGrindTarget> scoreLocalTargets(Character bot,
                                                             Point botPos,
                                                             Foothold botFoothold,
                                                             List<Monster> candidates) {
        List<ScoredGrindTarget> scoredTargets = new ArrayList<>(candidates.size());
        for (Monster candidate : candidates) {
            long localScore = grindTargetScore(bot, botPos, botFoothold, candidate);
            scoredTargets.add(new ScoredGrindTarget(candidate, localScore, localScore,
                    candidate.getPosition().distanceSq(botPos)));
        }
        return scoredTargets;
    }

    private static List<ScoredGrindTarget> scoreTargetRegions(GrindGraphContext context,
                                                              Character bot,
                                                              Point botPos,
                                                              Foothold botFoothold,
                                                              List<Monster> candidates) {
        Map<Integer, GrindTargetGroup> groupsByRegionId = new HashMap<>();
        for (Monster candidate : candidates) {
            Point targetPos = candidate.getPosition();
            int targetRegionId = BotNavigationManager.resolveTargetRegionId(
                    context.graph(), context.entry(), context.map(), targetPos);
            if (targetRegionId < 0) {
                continue;
            }

            long localScore = grindTargetScore(bot, botPos, botFoothold, candidate);
            GrindTargetGroup group = groupsByRegionId.computeIfAbsent(targetRegionId, GrindTargetGroup::new);
            group.add(candidate, localScore, targetPos.distanceSq(botPos));
        }

        List<ScoredGrindTarget> scoredTargets = new ArrayList<>(groupsByRegionId.size());
        for (GrindTargetGroup group : groupsByRegionId.values()) {
            long pathCost = graphPathCost(context.graph(), context.map(), context.startPos(), context.startRegionId(),
                    group.bestMonster().getPosition(), group.regionId(), context.profile());
            long crowdBonus = Math.min(3_000L, (long) Math.max(0, group.mobCount() - 1) * 400L);
            long graphScore = pathCost >= UNREACHABLE_GRAPH_COST
                    ? UNREACHABLE_GRAPH_COST
                    : Math.max(0L, pathCost - crowdBonus);
            scoredTargets.add(new ScoredGrindTarget(group.bestMonster(), graphScore, group.bestLocalScore(),
                    group.bestDistanceSq()));
        }
        return scoredTargets;
    }

    private static Monster pickFromBestTargets(List<ScoredGrindTarget> scoredTargets) {
        if (scoredTargets.isEmpty()) {
            return null;
        }
        scoredTargets.sort(Comparator
                .comparingLong(ScoredGrindTarget::graphCost)
                .thenComparingLong(ScoredGrindTarget::localScore)
                .thenComparingDouble(ScoredGrindTarget::distanceSq));
        return scoredTargets.get(ThreadLocalRandom.current().nextInt(Math.min(3, scoredTargets.size()))).monster();
    }

    private static long graphTargetCost(GrindGraphContext context, Monster target) {
        Point targetPos = target.getPosition();
        int targetRegionId = BotNavigationManager.resolveTargetRegionId(
                context.graph(), context.entry(), context.map(), targetPos);
        if (targetRegionId < 0) {
            return UNREACHABLE_GRAPH_COST;
        }

        return graphPathCost(context.graph(), context.map(), context.startPos(), context.startRegionId(),
                targetPos, targetRegionId, context.profile());
    }

    private static long graphPathCost(BotNavigationGraph graph,
                                      MapleMap map,
                                      Point startPos,
                                      int startRegionId,
                                      Point targetPos,
                                      int targetRegionId,
                                      BotMovementProfile profile) {
        if (startPos == null || targetPos == null || startRegionId < 0 || targetRegionId < 0) {
            return UNREACHABLE_GRAPH_COST;
        }
        if (startRegionId == targetRegionId) {
            return estimateLocalTravelCostMs(startPos, targetPos, profile);
        }

        List<BotNavigationGraph.Edge> path = BotNavigationManager.findPathForTargetScore(
                graph, map, startPos, startRegionId, targetRegionId, targetPos);
        if (path.isEmpty()) {
            return UNREACHABLE_GRAPH_COST;
        }

        long cost = 0;
        for (BotNavigationGraph.Edge edge : path) {
            cost += edge.cost;
        }
        return cost;
    }

    private static long estimateLocalTravelCostMs(Point from, Point to, BotMovementProfile profile) {
        int dx = Math.abs(to.x - from.x);
        int dy = Math.abs(to.y - from.y);
        double walkVelocity = Math.max(1.0, profile.walkVelocityPxs());
        return Math.round(dx * 1000.0 / walkVelocity) + dy * 4L;
    }

    private static long grindTargetScore(Character bot, Point botPos, Foothold botFoothold, Monster target) {
        Point targetPos = target.getPosition();
        Foothold targetFoothold = findGroundFoothold(targetPos, bot);

        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        boolean sameFoothold = botFoothold != null && targetFoothold != null && botFoothold.getId() == targetFoothold.getId();
        boolean nearSameLevel = dy <= BotCombatManager.cfg.ATTACK_RANGE_Y;

        long score = dx;
        score += (long) dy * 8L;
        if (!nearSameLevel) {
            score += 600L;
        }
        if (!sameFoothold) {
            score += 1200L;
        }
        return score;
    }

    private static Foothold findGroundFoothold(Point position, Character bot) {
        if (position == null || bot == null || bot.getMap() == null) {
            return null;
        }

        return BotPhysicsEngine.findGroundFoothold(bot.getMap(), position);
    }

    private record ScoredGrindTarget(Monster monster, long graphCost, long localScore, double distanceSq) {
    }

    private static final class GrindTargetGroup {
        private final int regionId;
        private int mobCount;
        private Monster bestMonster;
        private long bestLocalScore = Long.MAX_VALUE;
        private double bestDistanceSq = Double.MAX_VALUE;

        private GrindTargetGroup(int regionId) {
            this.regionId = regionId;
        }

        private void add(Monster monster, long localScore, double distanceSq) {
            mobCount++;
            if (bestMonster == null
                    || localScore < bestLocalScore
                    || (localScore == bestLocalScore && distanceSq < bestDistanceSq)) {
                bestMonster = monster;
                bestLocalScore = localScore;
                bestDistanceSq = distanceSq;
            }
        }

        private int regionId() {
            return regionId;
        }

        private int mobCount() {
            return mobCount;
        }

        private Monster bestMonster() {
            return bestMonster;
        }

        private long bestLocalScore() {
            return bestLocalScore;
        }

        private double bestDistanceSq() {
            return bestDistanceSq;
        }
    }

    private record GrindGraphContext(BotEntry entry,
                                     MapleMap map,
                                     BotNavigationGraph graph,
                                     BotMovementProfile profile,
                                     Point startPos,
                                     int startRegionId) {
        static GrindGraphContext resolve(BotEntry entry, Character bot, Point botPos) {
            if (entry == null || bot == null || bot.getMap() == null || bot.getMap().getFootholds() == null) {
                return unavailable(entry, bot, botPos);
            }

            BotMovementProfile profile = entry.movementProfile == null ? BotMovementProfile.fromCharacter(bot) : entry.movementProfile;
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
            BotMovementProfile profile = entry == null ? BotMovementProfile.base() : entry.movementProfile;
            Point startPos = botPos == null ? null : new Point(botPos);
            return new GrindGraphContext(entry, map, null, profile, startPos, -1);
        }

        boolean available() {
            return graph != null && map != null && startPos != null && startRegionId >= 0 && entry != null;
        }
    }

    static boolean isMobTouchingBot(BotEntry entry, Character bot, Monster mob) {
        Rectangle botBounds = getBotTouchBounds(entry, bot);
        Rectangle mobBounds = BotMobHitboxProvider.getInstance().getMobBounds(mob);
        if (mobBounds == null) {
            return false;
        }
        return mobBounds.intersects(botBounds);
    }

    static Rectangle getBotTouchBounds(BotEntry entry, Character bot) {
        Point currentPos = bot.getPosition();
        Point previousPos = currentPos;
        if (entry != null
                && entry.lastMobTouchCheckPos != null
                && entry.lastMobTouchMapId == bot.getMapId()) {
            previousPos = entry.lastMobTouchCheckPos;
        }

        // Mirror the client touch check: sweep the player's foot position between ticks
        // and use a fixed height above the feet instead of the full character sprite.
        int left = Math.min(previousPos.x, currentPos.x);
        int right = Math.max(previousPos.x, currentPos.x);
        int top = Math.min(previousPos.y, currentPos.y) - BotCombatManager.cfg.MOB_TOUCH_SWEEP_HEIGHT;
        int bottom = Math.max(previousPos.y, currentPos.y);
        return inclusiveRectangle(left, top, right, bottom);
    }

    private static Rectangle inclusiveRectangle(int left, int top, int right, int bottom) {
        return new Rectangle(left, top, Math.max(1, right - left + 1), Math.max(1, bottom - top + 1));
    }

    private static void rememberMobTouchCheck(BotEntry entry, Character bot, Point position) {
        if (entry == null || bot == null || position == null) {
            return;
        }

        entry.lastMobTouchCheckPos = new Point(position);
        entry.lastMobTouchMapId = bot.getMapId();
    }

    private static boolean doesHitBoxIntersectMonster(Rectangle hitBox, Monster monster) {
        if (hitBox == null || monster == null) {
            return false;
        }

        Rectangle mobBounds = BotMobHitboxProvider.getInstance().getMobBounds(monster);
        if (mobBounds != null) {
            return hitBox.intersects(mobBounds) || hitBox.contains(monster.getPosition());
        }

        return hitBox.contains(monster.getPosition());
    }

    private static boolean isForwardProjectileHitBox(Rectangle hitBox, Point botPos) {
        if (hitBox == null || botPos == null) {
            return false;
        }
        return botPos.x < hitBox.getMinX() || botPos.x > hitBox.getMaxX();
    }

    static Monster resolveEffectivePrimary(Character bot, Monster fallback, Rectangle hitBox) {
        Point botPos = bot.getPosition();
        if (!isForwardProjectileHitBox(hitBox, botPos)) {
            return fallback;
        }
        Monster closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (!m.isAlive() || !doesHitBoxIntersectMonster(hitBox, m)) {
                continue;
            }
            double distSq = m.getPosition().distanceSq(botPos);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = m;
            }
        }
        return closest != null ? closest : fallback;
    }

    static Monster findClosestAliveMonster(Character bot, double maxRangeSq) {
        Point botPos = bot.getPosition();
        Monster closest = null;
        double closestDistSq = maxRangeSq;
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (!m.isAlive()) {
                continue;
            }
            double distSq = m.getPosition().distanceSq(botPos);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = m;
            }
        }
        return closest;
    }

    private static BotAttackExecutionProvider.BasicAttackData buildBasicAttackData(Character bot, Monster primaryTarget) {
        return BotAttackExecutionProvider.buildBasicAttackData(bot, primaryTarget.getPosition());
    }

    static String describeDebugStats(BotEntry entry, Character bot) {
        Monster target = entry.grindTarget;
        if (target == null || !target.isAlive()) {
            target = findGrindTarget(bot);
        }

        AttackPlan plan = target != null ? planAttack(entry, bot, target) : null;
        String route = plan != null ? plan.route.name().toLowerCase() : BotAttackExecutionProvider.determineBasicAttackRoute(bot).name().toLowerCase();
        int speed = plan != null ? plan.speed : BotAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition()).speed();
        double cooldownSeconds = (plan != null ? plan.cooldownMs : 0) / 1000.0;
        double remainingSeconds = entry.attackCooldownMs / 1000.0;
        String targetName = target != null ? target.getName() : "none";

        return String.format(
                "debug: route %s, atk speed %d, atk cd %.2fs, remaining %.2fs, tick %dms, ai %dms, target %s",
                route, speed, cooldownSeconds, remainingSeconds,
                BotMovementManager.cfg.TICK_MS, BotManager.cfg.AI_TICK_MS, targetName);
    }

    private static boolean trySupportBuff(BotEntry entry, Character bot, long now) {
        for (int skillId : entry.buffSkillIds) {
            if (!isPartySupportSkill(skillId)) {
                continue;
            }
            if (bot.skillIsCooling(skillId)) {
                continue;
            }
            if (now < entry.nextSupportBuffAt.getOrDefault(skillId, 0L)) {
                continue;
            }

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) {
                continue;
            }

            StatEffect fx = skill.getEffect(lvl);
            if (!hasNearbyPartyMemberMissingBuff(bot, fx)) {
                continue;
            }

            if (castSupportSkill(entry, bot, skill, fx, now)) {
                entry.nextSupportBuffAt.put(skillId, now + cfg.SUPPORT_REBUFF_CD_MS);
                return true;
            }
        }

        return false;
    }

    private static boolean castSupportSkill(BotEntry entry, Character bot, Skill skill, StatEffect fx, long now) {
        if (!fx.canPaySkillCost(bot)) {
            return false;
        }
        if (!fx.applyTo(bot, null)) {
            return false;
        }

        long dur = fx.getDuration();
        if (dur > 0) {
            entry.nextBuffAt.put(skill.getId(), now + (long) (dur * 0.9));
        }
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData =
                BotAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, bot.getSkillLevel(skill),
                BotAttackExecutionProvider.getEquippedWeaponType(bot));
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs, skillTiming.cooldownMs());
        if (fx.getCooldown() > 0) {
            bot.addCooldown(skill.getId(), now, fx.getCooldown() * 1000L);
        }
        return true;
    }

    private static boolean canUseSkill(Character bot, int skillId, int skillLevel) {
        Skill skill = SkillFactory.getSkill(skillId);
        if (skill == null || skillLevel <= 0) {
            return false;
        }

        return skill.getEffect(skillLevel).canPaySkillCost(bot);
    }

    /** Returns true if the bot's weapon type requires projectile ammo. */
    static boolean isRangedAmmoWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.CLAW || weaponType == WeaponType.GUN;
    }

    /**
     * Periodically checks ammo for ranged bots. Piggybacks on the pot-check timer.
     * Warns at AMMO_LOW_WARN, stops grinding and follows owner at 0.
     */
    static void tickAmmoCheck(BotEntry entry, Character bot) {
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean mage = weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF;
        if (!mage && !isRangedAmmoWeapon(weaponType)) {
            entry.noAmmo = false;
            entry.ammoWarnSent = false;
            return;
        }

        if (mage) {
            int[] pots = BotPotionManager.countPotions(bot);
            int mpPots = pots[1];
            if (mpPots > 0) {
                entry.noAmmo = false;
                return;
            }
            if (!entry.noAmmo) {
                entry.noAmmo = true;
                if (entry.grinding) {
                    entry.grinding = false;
                    entry.following = true;
                    BotManager.getInstance().botSay(bot, BotManager.randomReply(MP_POTS_OUT_MSGS));
                }
            }
            return;
        }

        int ammo = countAmmo(bot, weaponType);
        if (ammo >= cfg.AMMO_LOW_WARN) {
            entry.noAmmo = false;
            entry.ammoWarnSent = false;
            return;
        }

        if (ammo > 0 && !entry.ammoWarnSent) {
            entry.ammoWarnSent = true;
            BotManager.getInstance().botSay(bot, BotManager.randomReply(AMMO_LOW_MSGS));
            return;
        }

        if (ammo <= 0 && !entry.noAmmo) {
            entry.noAmmo = true;
            if (entry.grinding) {
                entry.grinding = false;
                entry.following = true;
                BotManager.getInstance().botSay(bot, BotManager.randomReply(AMMO_OUT_MSGS));
            }
        }
    }

    /** Counts total ammo in USE inventory matching the bot's equipped weapon type. */
    static int countAmmo(Character bot, WeaponType weaponType) {
        if (weaponType == null || !isRangedAmmoWeapon(weaponType)) {
            return Integer.MAX_VALUE;
        }
        boolean soulArrow = bot.getBuffedValue(BuffStat.SOULARROW) != null;
        boolean shadowClaw = bot.getBuffedValue(BuffStat.SHADOW_CLAW) != null;
        if (soulArrow || shadowClaw) {
            return Integer.MAX_VALUE;
        }
        int total = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            boolean match = switch (weaponType) {
                case BOW -> ItemConstants.isArrowForBow(id);
                case CROSSBOW -> ItemConstants.isArrowForCrossBow(id);
                case CLAW -> ItemConstants.isThrowingStar(id);
                case GUN -> ItemConstants.isBullet(id);
                default -> false;
            };
            if (match) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    private static boolean hasNearbyPartyMemberMissingBuff(Character bot, StatEffect fx) {
        if (fx.getStatups().isEmpty()) {
            return false;
        }

        for (Character target : getNearbyPartyMembers(bot)) {
            for (var statup : fx.getStatups()) {
                if (target.getBuffedValue(statup.getLeft()) == null) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasNearbyPartyMemberNeedingHeal(Character bot) {
        for (Character target : getNearbyPartyMembers(bot)) {
            int maxHp = target.getCurrentMaxHp();
            if (maxHp <= 0) {
                continue;
            }

            int missingHp = maxHp - target.getHp();
            if (missingHp >= cfg.SUPPORT_HEAL_MISSING_HP
                    && target.getHp() <= Math.round(maxHp * cfg.SUPPORT_HEAL_RATIO)) {
                return true;
            }
        }

        return false;
    }

    private static List<Character> getNearbyPartyMembers(Character bot) {
        List<Character> nearby = new ArrayList<>();
        Point botPos = bot.getPosition();
        double rangeSq = (double) cfg.SUPPORT_RANGE * cfg.SUPPORT_RANGE;
        for (Character member : bot.getPartyMembersOnSameMap()) {
            if (member == null || member.getId() == bot.getId() || !member.isAlive()) {
                continue;
            }

            Point memberPos = member.getPosition();
            if (Math.abs(memberPos.y - botPos.y) > cfg.SUPPORT_VERTICAL_RANGE) {
                continue;
            }
            if (memberPos.distanceSq(botPos) > rangeSq) {
                continue;
            }

            nearby.add(member);
        }
        return nearby;
    }

    private static boolean isPartySupportSkill(int skillId) {
        return PARTY_SUPPORT_SKILL_IDS.contains(skillId);
    }

    private static boolean isHealSkill(int skillId) {
        return skillId == Cleric.HEAL || skillId == SuperGM.HEAL_PLUS_DISPEL;
    }
}
