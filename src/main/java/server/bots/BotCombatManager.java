package server.bots;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.WeaponType;
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
import constants.skills.Spearman;
import constants.skills.SuperGM;
import constants.skills.ThunderBreaker;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.StatEffect;
import server.bots.combat.BotCharacterHitboxProvider;
import server.bots.combat.BotCombatFormulaProvider;
import server.bots.combat.BotDefenseDataProvider;
import server.bots.combat.BotMobHitboxProvider;
import server.life.Monster;
import server.maps.Foothold;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

class BotCombatManager {

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
        public int   RANGED_RETREAT_DISTANCE_X = 120;

        // Grind / AoE
        public int   GRIND_SEEK_RANGE  = 800;
        public int   AOE_MOB_THRESHOLD = 2;

        // Mob damage
        public int   MOB_TOUCH_HALF_W = 40;
        public int   MOB_TOUCH_HALF_H = 30;
        public int   MOB_HIT_COOLDOWN_MS = 1500;
        public long  BOT_DEAD_MS      = 10_000L;

        // Support
        public int   SUPPORT_RANGE = 400;
        public int   SUPPORT_VERTICAL_RANGE = 220;
        public int   SUPPORT_REBUFF_CD_MS = 3_000;
        public int   SUPPORT_HEAL_CD_MS = 2_000;
        public float SUPPORT_HEAL_RATIO = 0.75f;
        public int   SUPPORT_HEAL_MISSING_HP = 250;
    }

    static Config cfg = new Config();
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

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    static void tickMobDamage(BotEntry entry, Character bot) {
        if (entry.mobHitCooldownMs > 0) {
            entry.mobHitCooldownMs = BotMovementManager.tickDown(entry.mobHitCooldownMs);
            return;
        }
        if (bot.getHp() <= 0) return;

        for (Monster mob : bot.getMap().getAllMonsters()) {
            if (!mob.isAlive()) continue;
            if (isMobTouchingBot(bot, mob)) {
                applyMobHit(entry, bot, mob);
                return;
            }
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
        int bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            int atk = fx.getAttackCount();
            int mobs = fx.getMobCount();
            int dur = fx.getDuration();

            if (isHealSkill(skill.getId())) {
                entry.healSkillId = skill.getId();
            }

            if (atk > 0) {
                if (mobs >= 2) {
                    int score = mobs * atk;
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        entry.aoeSkillId = skill.getId();
                        entry.aoeSkillMobs = mobs;
                    }
                } else if (atk > bestAtkHits) {
                    bestAtkHits = atk;
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
            castSupportSkill(entry, bot, skill, fx, now);
            return;
        }
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
        fx.applyTo(bot);

        entry.nextSupportHealAt = now + cfg.SUPPORT_HEAL_CD_MS;
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs,
                BotMovementManager.delayAfterCurrentTick(Math.max(0, skill.getAnimationTime())));
        if (fx.getCooldown() > 0) {
            bot.addCooldown(entry.healSkillId, now, fx.getCooldown() * 1000L);
        }
    }

    /** Returns a random monster from the nearest 3 within seek range, so multiple bots spread across targets. */
    static Monster findGrindTarget(Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Foothold botFoothold = findGroundFoothold(botPos, bot);
            List<Monster> candidates = new ArrayList<>();
            for (Monster m : bot.getMap().getAllMonsters()) {
                if (m.isAlive() && m.getPosition().distanceSq(botPos) <= rangeSq) {
                    candidates.add(m);
                }
            }
            if (candidates.isEmpty()) return null;

            candidates.sort((a, b) -> compareGrindTargets(bot, botPos, botFoothold, a, b));
            return candidates.get(ThreadLocalRandom.current().nextInt(Math.min(3, candidates.size())));
        } finally {
            BotPerformanceMonitor.record("combat-target-search", System.nanoTime() - startedAt);
        }
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
            return new AttackPlan(0, 0, 1, basicAttackData.hitBox(), List.of(target), basicAttackData.route(),
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

    static boolean isTargetJumpable(boolean closeRangeRoute, Point botPos, Point targetPos) {
        if (!closeRangeRoute || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        if (dx > BotCombatManager.cfg.ATTACK_RANGE_X + BotCombatManager.cfg.ATTACK_JUMP_X_EXTRA) {
            return false;
        }

        int dy = botPos.y - targetPos.y;
        return dy > BotCombatManager.cfg.ATTACK_RANGE_Y && dy <= BotCombatManager.cfg.ATTACK_JUMP_Y;
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        if (entry.attackCooldownMs > 0) {
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
        attack.stance = attackPlan.stance;
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction;
        attack.rangedirection = attackPlan.rangedDirection;
        attack.ranged = attackPlan.route == AttackRoute.RANGED;
        BotCombatFormulaProvider.DamageProfile damageProfile = BotCombatFormulaProvider.getInstance().resolveDamageProfile(
                bot, attackPlan.skillId, attackPlan.skillLevel,
                attackPlan.route == AttackRoute.MAGIC);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            attack.targets.put(target.getObjectId(),
                    BotCombatFormulaProvider.getInstance().makeTarget(bot, target, attackPlan.numDamage,
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
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget);
        if (hitBox == null) {
            return null;
        }

        List<Monster> targets = collectTargetsInHitBox(bot, primaryTarget, hitBox, Math.max(1, effect.getMobCount()));
        if (targets.size() < BotCombatManager.cfg.AOE_MOB_THRESHOLD) {
            return null;
        }

        int attackCount = Math.max(1, effect.getAttackCount());
        AttackRoute route = BotAttackExecutionProvider.determineSkillRoute(bot, entry.aoeSkillId);
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!BotAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BotAttackExecutionProvider.BasicAttackSpec attackSpec = BotAttackExecutionProvider.basicAttackSpec(weaponType);
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        BotAttackExecutionProvider.CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? BotAttackExecutionProvider.mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.direction()
                : BotAttackExecutionProvider.basicAttackDirectionId(action, fallbackAction);
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.aoeSkillId, skillLevel, attackCount, hitBox, targets,
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction, route == AttackRoute.CLOSE ? closeRangePacketFields.stance() : 0,
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
        Rectangle hitBox = calculateSkillHitBox(effect, bot, primaryTarget);
        if (hitBox == null || !doesHitBoxIntersectMonster(hitBox, primaryTarget)) {
            return null;
        }

        int attackCount = Math.max(1, effect.getAttackCount());
        AttackRoute route = BotAttackExecutionProvider.determineSkillRoute(bot, entry.attackSkillId);
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!BotAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BotAttackExecutionProvider.BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BotAttackExecutionProvider.BasicAttackSpec attackSpec = BotAttackExecutionProvider.basicAttackSpec(weaponType);
        String action = BotAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        BotAttackExecutionProvider.CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? BotAttackExecutionProvider.mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.direction()
                : BotAttackExecutionProvider.basicAttackDirectionId(action, fallbackAction);
        BotAttackExecutionProvider.SkillAttackTiming skillTiming =
                BotAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.attackSkillId, skillLevel, attackCount, hitBox, List.of(primaryTarget),
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction, route == AttackRoute.CLOSE ? closeRangePacketFields.stance() : 0,
                fallbackAttackData.speed(), skillTiming.hitDelayMs(), skillTiming.cooldownMs());
    }

    private static Rectangle calculateSkillHitBox(StatEffect effect, Character bot, Monster primaryTarget) {
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        if (effect.hasBoundingBox()) {
            return effect.calculateBoundingBox(bot.getPosition(), facingLeft);
        }

        return fallbackCloseRangeSkillHitBox(effect, bot, facingLeft);
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

    private static int compareGrindTargets(Character bot, Point botPos, Foothold botFoothold, Monster left, Monster right) {
        long leftScore = grindTargetScore(bot, botPos, botFoothold, left);
        long rightScore = grindTargetScore(bot, botPos, botFoothold, right);
        if (leftScore != rightScore) {
            return Long.compare(leftScore, rightScore);
        }

        return Double.compare(left.getPosition().distanceSq(botPos), right.getPosition().distanceSq(botPos));
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

    private static boolean isMobTouchingBot(Character bot, Monster mob) {
        Rectangle botBounds = getBotTouchBounds(bot);
        Rectangle mobBounds = BotMobHitboxProvider.getInstance().getMobBounds(mob);
        if (mobBounds != null) {
            return mobBounds.intersects(botBounds);
        }

        Point botPos = bot.getPosition();
        Point mobPos = mob.getPosition();
        Config cc = BotCombatManager.cfg;
        return Math.abs(botPos.x - mobPos.x) <= cc.MOB_TOUCH_HALF_W
                && Math.abs(botPos.y - mobPos.y) <= cc.MOB_TOUCH_HALF_H;
    }

    private static Rectangle getBotTouchBounds(Character bot) {
        Rectangle bounds = BotCharacterHitboxProvider.getInstance().getBotBounds(bot);
        if (bounds != null) {
            return bounds;
        }

        Point botPos = bot.getPosition();
        int halfWidth = BotCombatManager.cfg.MOB_TOUCH_HALF_W;
        int halfHeight = BotCombatManager.cfg.MOB_TOUCH_HALF_H;
        return new Rectangle(botPos.x - halfWidth, botPos.y - halfHeight, halfWidth * 2, halfHeight * 2);
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

            castSupportSkill(entry, bot, skill, fx, now);
            entry.nextSupportBuffAt.put(skillId, now + cfg.SUPPORT_REBUFF_CD_MS);
            return true;
        }

        return false;
    }

    private static void castSupportSkill(BotEntry entry, Character bot, Skill skill, StatEffect fx, long now) {
        fx.applyTo(bot, null);

        long dur = fx.getDuration();
        if (dur > 0) {
            entry.nextBuffAt.put(skill.getId(), now + (long) (dur * 0.9));
        }
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs,
                BotMovementManager.delayAfterCurrentTick(Math.max(0, skill.getAnimationTime())));
        if (fx.getCooldown() > 0) {
            bot.addCooldown(skill.getId(), now, fx.getCooldown() * 1000L);
        }
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
