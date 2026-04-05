package server.bots;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.BlazeWizard;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.Cleric;
import constants.skills.Corsair;
import constants.skills.DawnWarrior;
import constants.skills.DragonKnight;
import constants.skills.Evan;
import constants.skills.Fighter;
import constants.skills.FPMage;
import constants.skills.GM;
import constants.skills.Hermit;
import constants.skills.ILMage;
import constants.skills.Marksman;
import constants.skills.NightLord;
import constants.skills.NightWalker;
import constants.skills.Priest;
import constants.skills.Rogue;
import constants.skills.Shadower;
import constants.skills.Spearman;
import constants.skills.SuperGM;
import constants.skills.ThunderBreaker;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import net.server.channel.handlers.MagicDamageHandler;
import net.server.channel.handlers.RangedAttackHandler;
import server.StatEffect;
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
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs, toCooldownMs(resolveSkillAttackDelayMillis(skill)));
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

            BasicAttackData basicAttackData = buildBasicAttackData(bot, target);
            return new AttackPlan(0, 0, 1, basicAttackData.hitBox, List.of(target), basicAttackData.route,
                    basicAttackData.display, basicAttackData.direction, basicAttackData.rangedDirection, basicAttackData.stance,
                    basicAttackData.speed, basicAttackData.hitDelayMs, basicAttackData.cooldownMs);
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
        DamageProfile damageProfile = resolveDamageProfile(bot, attackPlan.skillId, attackPlan.skillLevel,
                attackPlan.route == AttackRoute.MAGIC);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            int[] adj = damageProfile.alwaysHit()
                    ? new int[]{damageProfile.minDamage(), damageProfile.maxDamage()}
                    : applyMonsterDefense(bot, target, damageProfile.minDamage(), damageProfile.maxDamage(),
                    damageProfile.magicAttack());
            attack.targets.put(target.getObjectId(),
                    makeTarget(bot, target, attackPlan.numDamage, adj[0], adj[1], attackPlan.hitDelayMs,
                            damageProfile.magicAttack(), damageProfile.alwaysHit()));
        }

        applyAttackRoute(attackPlan.route, attack, bot);
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
        AttackRoute route = determineSkillRoute(bot, entry.aoeSkillId);
        WeaponType weaponType = getEquippedWeaponType(bot);
        if (!canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BasicAttackSpec attackSpec = resolveWeaponAttackSpec(bot, weaponType);
        String action = resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.direction()
                : basicAttackDirectionId(action, fallbackAction);
        SkillAttackTiming skillTiming = resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.aoeSkillId, skillLevel, attackCount, hitBox, targets,
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction, route == AttackRoute.CLOSE ? closeRangePacketFields.stance() : 0,
                resolveWeaponAttackSpeed(bot), skillTiming.hitDelayMs(), skillTiming.cooldownMs());
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
        AttackRoute route = determineSkillRoute(bot, entry.attackSkillId);
        WeaponType weaponType = getEquippedWeaponType(bot);
        if (!canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        BasicAttackData fallbackAttackData = buildBasicAttackData(bot, primaryTarget);
        BasicAttackSpec attackSpec = resolveWeaponAttackSpec(bot, weaponType);
        String action = resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        String fallbackAction = attackSpec.primaryAction();
        CloseRangePacketFields closeRangePacketFields = route == AttackRoute.CLOSE
                ? mimicCloseRangePacketFields(action, fallbackAction, facingLeft)
                : null;
        int direction = route == AttackRoute.CLOSE
                ? closeRangePacketFields.direction()
                : basicAttackDirectionId(action, fallbackAction);
        SkillAttackTiming skillTiming = resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        return new AttackPlan(entry.attackSkillId, skillLevel, attackCount, hitBox, List.of(primaryTarget),
                route, route == AttackRoute.CLOSE ? closeRangePacketFields.display() : 0,
                direction, direction, route == AttackRoute.CLOSE ? closeRangePacketFields.stance() : 0,
                resolveWeaponAttackSpeed(bot), skillTiming.hitDelayMs(), skillTiming.cooldownMs());
    }

    private static Rectangle calculateSkillHitBox(StatEffect effect, Character bot, Monster primaryTarget) {
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        if (effect.hasBoundingBox()) {
            return effect.calculateBoundingBox(bot.getPosition(), facingLeft);
        }

        return fallbackCloseRangeSkillHitBox(effect, bot, facingLeft);
    }

    static Rectangle fallbackCloseRangeSkillHitBox(StatEffect effect, Character bot, boolean facingLeft) {
        if (effect == null || bot == null || determineWeaponRoute(getEquippedWeaponType(bot)) != AttackRoute.CLOSE) {
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

    static boolean shouldDegenerateRangedAttack(WeaponType weaponType, Point botPos, Point targetPos) {
        if (!isDegenerateCapableRangedWeapon(weaponType) || botPos == null || targetPos == null) {
            return false;
        }

        int dx = Math.abs(targetPos.x - botPos.x);
        int dy = Math.abs(targetPos.y - botPos.y);
        return dx <= BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_X
                && dy <= BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_Y;
    }

    static boolean shouldRetreatFromNearbyTarget(WeaponType weaponType, Point botPos, Point targetPos) {
        return shouldDegenerateRangedAttack(weaponType, botPos, targetPos)
                && !isBasicAttackInRange(botPos, targetPos);
    }

    static boolean shouldRetreatFromNearbyTarget(Character bot, Monster target) {
        if (bot == null || target == null) {
            return false;
        }
        return shouldRetreatFromNearbyTarget(getEquippedWeaponType(bot), bot.getPosition(), target.getPosition());
    }

    static Point retreatTargetPosition(Point botPos, Point targetPos) {
        if (botPos == null) {
            return targetPos == null ? null : new Point(targetPos);
        }
        if (targetPos == null) {
            return new Point(botPos);
        }

        int direction = Integer.compare(botPos.x, targetPos.x);
        if (direction == 0) {
            direction = 1;
        }
        return new Point(botPos.x + direction * BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X, botPos.y);
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

    private static BasicAttackData buildBasicAttackData(Character bot, Monster primaryTarget) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return BasicAttackData.fallback(primaryTarget.getPosition().x < bot.getPosition().x);
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return BasicAttackData.fallback(primaryTarget.getPosition().x < bot.getPosition().x);
        }

        WeaponType weaponType = server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
        boolean facingLeft = primaryTarget.getPosition().x < bot.getPosition().x;
        return BasicAttackData.fromProfile(attackProfile, weaponType, facingLeft, bot, primaryTarget.getPosition());
    }

    static record BasicAttackSpec(int display, List<String> actions) {
        String primaryAction() {
            return actions.isEmpty() ? "swingO1" : actions.get(0);
        }

        String actionForVariant(int variantOffset) {
            if (actions.isEmpty()) {
                return "swingO1";
            }
            int normalizedIndex = Math.max(0, Math.min(variantOffset, actions.size() - 1));
            return actions.get(normalizedIndex);
        }

        int stanceIdForVariant(int variantOffset) {
            return attackStanceId(actionForVariant(variantOffset));
        }
    }

    /**
     * Maps weapon types to the same attack display groups and action pools used by
     * OpenStory's {@code CharLook::getattackstance} regular-attack selection.
     */
    static BasicAttackSpec basicAttackSpec(int attackGroup, WeaponType fallbackWeaponType) {
        return basicAttackSpec(attackGroup, fallbackWeaponType, false);
    }

    static BasicAttackSpec basicAttackSpec(int attackGroup, WeaponType fallbackWeaponType, boolean degenerate) {
        if (degenerate) {
            return switch (attackGroup) {
                case 3 -> new BasicAttackSpec(3, List.of("swingT1", "swingT3"));
                case 4 -> new BasicAttackSpec(4, List.of("swingT1", "stabT1"));
                case 7 -> new BasicAttackSpec(7, List.of("swingT1", "stabT1"));
                case 9 -> new BasicAttackSpec(9, List.of("swingP1", "stabT2"));
                default -> basicAttackSpec(fallbackWeaponType, false);
            };
        }

        return switch (attackGroup) {
            case 1 -> new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
            case 2 -> new BasicAttackSpec(2, List.of("stabT1", "swingP1"));
            case 3 -> new BasicAttackSpec(3, List.of("shoot1"));
            case 4 -> new BasicAttackSpec(4, List.of("shoot2"));
            case 5 -> new BasicAttackSpec(5, List.of("stabO1", "stabO2", "swingT1", "swingT2", "swingT3"));
            case 6 -> new BasicAttackSpec(6, List.of("swingO1", "swingO2"));
            case 7 -> new BasicAttackSpec(7, List.of("swingO1", "swingO2"));
            case 9 -> new BasicAttackSpec(9, List.of("handgun"));
            default -> basicAttackSpec(fallbackWeaponType);
        };
    }

    static BasicAttackSpec basicAttackSpec(WeaponType weaponType) {
        return basicAttackSpec(weaponType, false);
    }

    static BasicAttackSpec basicAttackSpec(WeaponType weaponType, boolean degenerate) {
        if (weaponType == null) {
            return new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
        }
        if (degenerate) {
            return switch (weaponType) {
                case BOW -> new BasicAttackSpec(3, List.of("swingT1", "swingT3"));
                case CROSSBOW -> new BasicAttackSpec(4, List.of("swingT1", "stabT1"));
                case CLAW -> new BasicAttackSpec(7, List.of("swingT1", "stabT1"));
                case GUN -> new BasicAttackSpec(9, List.of("swingP1", "stabT2"));
                default -> basicAttackSpec(weaponType, false);
            };
        }
        return switch (weaponType) {
            case BOW -> new BasicAttackSpec(3, List.of("shoot1"));
            case CROSSBOW -> new BasicAttackSpec(4, List.of("shoot2"));
            case SPEAR_SWING, SPEAR_STAB, POLE_ARM_SWING, POLE_ARM_STAB ->
                    new BasicAttackSpec(2, List.of("stabT1", "swingP1"));
            case GENERAL2H_SWING, GENERAL2H_STAB, SWORD2H ->
                    new BasicAttackSpec(5, List.of("stabO1", "stabO2", "swingT1", "swingT2", "swingT3"));
            case WAND, STAFF -> new BasicAttackSpec(6, List.of("swingO1", "swingO2"));
            case CLAW -> new BasicAttackSpec(7, List.of("swingO1", "swingO2"));
            case GUN -> new BasicAttackSpec(9, List.of("handgun"));
            default -> new BasicAttackSpec(1, List.of("stabO1", "stabO2", "swingO1", "swingO2", "swingO3"));
        };
    }

    static int attackStanceId(String actionName) {
        return switch (actionName) {
            case "shot" -> 10;
            case "handgun" -> 10;
            case "shoot1" -> 11;
            case "shoot2" -> 12;
            case "stabO1" -> 15;
            case "stabO2" -> 16;
            case "stabT1" -> 18;
            case "stabT2" -> 19;
            case "swingO1" -> 23;
            case "swingO2" -> 24;
            case "swingO3" -> 25;
            case "swingP1" -> 27;
            case "swingP2" -> 28;
            case "swingT1" -> 30;
            case "swingT2" -> 31;
            case "swingT3" -> 32;
            default -> 0;
        };
    }

    static int basicAttackDirectionId(String actionName, String fallbackAction) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int actionId = provider.getBodyActionId(actionName);
        if (actionId >= 0) {
            return actionId;
        }

        if (fallbackAction != null && !fallbackAction.equals(actionName)) {
            int fallbackActionId = provider.getBodyActionId(fallbackAction);
            if (fallbackActionId >= 0) {
                return fallbackActionId;
            }
        }

        return 0;
    }

    // Captured close-range recv packets on this client use display=0, encode the
    // body action id in the direction byte, and use stance as a facing flag:
    // 0x00 facing right, 0x80 facing left.
    static CloseRangePacketFields mimicCloseRangePacketFields(String actionName, String fallbackAction, boolean facingLeft) {
        return new CloseRangePacketFields(0,
                basicAttackDirectionId(actionName, fallbackAction),
                facingLeft ? 0x80 : 0x00);
    }

    static List<String> resolveAttackActions(BasicAttackSpec attackSpec, List<String> sourceActions) {
        if (attackSpec == null || attackSpec.actions().isEmpty()) {
            return List.of("swingO1");
        }

        if (sourceActions == null || sourceActions.isEmpty()) {
            return attackSpec.actions();
        }

        List<String> resolvedActions = new ArrayList<>();
        for (String attackAction : attackSpec.actions()) {
            if (sourceActions.contains(attackAction)) {
                resolvedActions.add(attackAction);
            }
        }

        return resolvedActions.isEmpty() ? attackSpec.actions() : List.copyOf(resolvedActions);
    }

    private static String sampleAttackAction(List<String> candidateActions, String fallbackAction) {
        if (candidateActions == null || candidateActions.isEmpty()) {
            return fallbackAction;
        }

        int variantOffset = ThreadLocalRandom.current().nextInt(candidateActions.size());
        return candidateActions.get(variantOffset);
    }

    private static BasicAttackSpec resolveWeaponAttackSpec(Character bot, WeaponType weaponType) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null && attackProfile.getAttack() > 0) {
                return basicAttackSpec(attackProfile.getAttack(), weaponType);
            }
        }
        return basicAttackSpec(weaponType);
    }

    private static String sampleWeaponAttackAction(Character bot, WeaponType weaponType) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon != null) {
            BotAttackDataProvider.NormalAttackProfile attackProfile =
                    BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
            if (attackProfile != null) {
                BasicAttackSpec attackSpec = basicAttackSpec(attackProfile.getAttack(), weaponType);
                return sampleAttackAction(resolveAttackActions(attackSpec, attackProfile.getSourceActions()),
                        attackSpec.primaryAction());
            }
        }

        BasicAttackSpec attackSpec = basicAttackSpec(weaponType);
        return sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
    }

    static String resolveSkillAttackAction(Character bot, Skill skill, int skillLevel, WeaponType weaponType) {
        if (skill != null) {
            boolean twoHanded = isTwoHandedWeapon(bot);
            String skillAction = skill.resolveAnimationAction(skillLevel, twoHanded);
            if (skillAction != null) {
                return skillAction;
            }
        }
        return sampleWeaponAttackAction(bot, weaponType);
    }

    private static void applyAttackRoute(AttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character bot) {
        switch (route) {
            case RANGED -> RangedAttackHandler.applyRangedAttackEffects(attack, bot, bot.getClient());
            case MAGIC -> MagicDamageHandler.applyMagicAttackEffects(attack, bot, bot.getClient());
            default -> CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        }
    }

    private static AttackRoute determineBasicAttackRoute(Character bot) {
        return determineBasicWeaponRoute(getEquippedWeaponType(bot));
    }

    private static boolean canUseRangedAttackRoute(AttackRoute route, WeaponType weaponType, Point botPos, Point targetPos) {
        return route != AttackRoute.RANGED || !shouldDegenerateRangedAttack(weaponType, botPos, targetPos);
    }

    private static AttackRoute determineSkillRoute(Character bot, int skillId) {
        if (isRangedSkill(skillId)) {
            return AttackRoute.RANGED;
        }

        WeaponType weaponType = getEquippedWeaponType(bot);
        if (weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF) {
            return AttackRoute.MAGIC;
        }

        return determineWeaponRoute(weaponType);
    }

    static AttackRoute determineBasicWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return AttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> AttackRoute.RANGED;
            default -> AttackRoute.CLOSE;
        };
    }

    private static AttackRoute determineWeaponRoute(WeaponType weaponType) {
        if (weaponType == null) {
            return AttackRoute.CLOSE;
        }

        return switch (weaponType) {
            case BOW, CROSSBOW, CLAW, GUN -> AttackRoute.RANGED;
            case WAND, STAFF -> AttackRoute.MAGIC;
            default -> AttackRoute.CLOSE;
        };
    }

    private static WeaponType getEquippedWeaponType(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return null;
        }

        return server.ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
    }

    private static boolean isTwoHandedWeapon(Character bot) {
        Item weapon = bot != null ? bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11) : null;
        if (weapon == null) {
            return false;
        }

        return server.ItemInformationProvider.getInstance().isTwoHanded(weapon.getItemId());
    }

    private static boolean isRangedSkill(int skillId) {
        return switch (skillId) {
            case constants.skills.Buccaneer.ENERGY_ORB,
                 constants.skills.ThunderBreaker.SPARK,
                 constants.skills.ThunderBreaker.SHARK_WAVE,
                 constants.skills.Shadower.TAUNT,
                 constants.skills.NightLord.TAUNT,
                 constants.skills.Aran.COMBO_SMASH,
                 constants.skills.Aran.COMBO_FENRIR,
                 constants.skills.Aran.COMBO_TEMPEST -> true;
            default -> false;
        };
    }

    private static boolean isDegenerateCapableRangedWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.BOW
                || weaponType == WeaponType.CROSSBOW
                || weaponType == WeaponType.CLAW
                || weaponType == WeaponType.GUN;
    }

    private static Rectangle closeRangeBasicHitBox(Point origin, boolean facingLeft) {
        int horizontalRange = BotCombatManager.cfg.ATTACK_RANGE_X;
        int top = origin.y - BotCombatManager.cfg.ATTACK_RANGE_Y;
        int height = BotCombatManager.cfg.ATTACK_RANGE_Y + BotCombatManager.cfg.ATTACK_DOWN_MAX;
        int left = facingLeft ? origin.x - horizontalRange : origin.x;
        return new Rectangle(left, top, horizontalRange, height);
    }

    private record BasicAttackData(Rectangle hitBox, int display, int direction, int rangedDirection,
                                   int stance, int speed, int hitDelayMs, int cooldownMs, AttackRoute route) {
        private static BasicAttackData fromProfile(BotAttackDataProvider.NormalAttackProfile profile, WeaponType weaponType,
                                                   boolean facingLeft, Character bot, Point targetPos) {
            int baseDisplay = profile.getAttack();
            boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                    bot != null ? bot.getPosition() : null, targetPos);
            BasicAttackSpec attackSpec = basicAttackSpec(baseDisplay, weaponType, useDegenerateCloseRange);
            if (baseDisplay <= 0) {
                return fallback(facingLeft, profile.getAttackSpeed(), weaponType, bot, targetPos);
            }

            String fallbackAction = attackSpec.primaryAction();
            List<String> candidateActions = resolveAttackActions(attackSpec, profile.getSourceActions());
            String action = sampleAttackAction(candidateActions, fallbackAction);
            int variantOffset = Math.max(0, attackSpec.actions().indexOf(action));
            AttackRoute route = useDegenerateCloseRange ? AttackRoute.CLOSE : determineBasicWeaponRoute(weaponType);
            boolean closeRangeRoute = route == AttackRoute.CLOSE;
            CloseRangePacketFields closeRangePacketFields = mimicCloseRangePacketFields(action, fallbackAction, facingLeft);
            int display = closeRangeRoute ? closeRangePacketFields.display() : baseDisplay + variantOffset;
            int direction = closeRangeRoute
                    ? closeRangePacketFields.direction()
                    : basicAttackDirectionId(action, fallbackAction);
            int effectiveAttackSpeed = resolveEffectiveAttackSpeed(profile.getAttackSpeed(), bot);
            BotAttackDataProvider provider = BotAttackDataProvider.getInstance();

            // OpenStory gates new attacks on the body animation ending, while hit effects land
            // when the afterimage first becomes active for the selected action.
            int rawAnimationDelayMs = provider.getBodyStanceDurationMs(action);
            if (rawAnimationDelayMs <= 0) {
                rawAnimationDelayMs = profile.getAttackDelayMillis();
            }
            int rawHitDelayMs = provider.getBodyStanceDelayBeforeFrameMs(action, profile.getAfterimageFirstFrame(action));

            int cooldownMs = toCooldownMs(adjustAttackDelayMillis(rawAnimationDelayMs, profile.getAttackSpeed(), effectiveAttackSpeed));
            int hitDelayMs = adjustAttackDelayMillis(rawHitDelayMs, profile.getAttackSpeed(), effectiveAttackSpeed);
            int stance = closeRangeRoute ? closeRangePacketFields.stance() : attackStanceId(action);
            Rectangle hitBox = closeRangeRoute
                    ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                    : profile.hasBoundingBox() ? profile.calculateBoundingBox(bot.getPosition(), facingLeft) : null;

            return new BasicAttackData(hitBox, display, direction, direction, stance, effectiveAttackSpeed,
                    hitDelayMs, cooldownMs, route);
        }

        private static BasicAttackData fallback(boolean facingLeft) {
            return fallback(facingLeft, 4, null, null, null);
        }

        private static BasicAttackData fallback(boolean facingLeft, int baseAttackSpeed,
                                                WeaponType weaponType, Character bot, Point targetPos) {
            boolean useDegenerateCloseRange = shouldDegenerateRangedAttack(weaponType,
                    bot != null ? bot.getPosition() : null, targetPos);
            BasicAttackSpec attackSpec = basicAttackSpec(weaponType, useDegenerateCloseRange);
            String action = sampleAttackAction(attackSpec.actions(), attackSpec.primaryAction());
            int variantOffset = Math.max(0, attackSpec.actions().indexOf(action));
            AttackRoute route = useDegenerateCloseRange ? AttackRoute.CLOSE : determineBasicWeaponRoute(weaponType);
            boolean closeRangeRoute = route == AttackRoute.CLOSE;
            CloseRangePacketFields closeRangePacketFields =
                    mimicCloseRangePacketFields(action, attackSpec.primaryAction(), facingLeft);
            int display = closeRangeRoute ? closeRangePacketFields.display() : attackSpec.display() + variantOffset;
            int direction = closeRangeRoute
                    ? closeRangePacketFields.direction()
                    : basicAttackDirectionId(action, attackSpec.primaryAction());
            int effectiveAttackSpeed = resolveEffectiveAttackSpeed(baseAttackSpeed, bot);
            int rawAnimationDelayMs = BotAttackDataProvider.getInstance().getBodyStanceDurationMs(action);
            if (rawAnimationDelayMs <= 0) {
                rawAnimationDelayMs = 600;
            }
            int adjustedAnimationDelayMs = adjustAttackDelayMillis(rawAnimationDelayMs, baseAttackSpeed, effectiveAttackSpeed);
            Rectangle hitBox = closeRangeRoute && bot != null
                    ? closeRangeBasicHitBox(bot.getPosition(), facingLeft)
                    : null;

            return new BasicAttackData(hitBox, display, direction, direction,
                    closeRangeRoute ? closeRangePacketFields.stance() : attackSpec.stanceIdForVariant(variantOffset),
                    effectiveAttackSpeed, defaultHitDelayMs(adjustedAnimationDelayMs), toCooldownMs(adjustedAnimationDelayMs),
                    route);
        }

        private static int countMoveVariants(List<String> sourceActions) {
            if (sourceActions == null || sourceActions.isEmpty()) {
                return 1;
            }

            int variantCount = 0;
            for (String sourceAction : sourceActions) {
                if (sourceAction == null || sourceAction.isBlank()) {
                    continue;
                }
                variantCount++;
            }
            return Math.max(1, variantCount);
        }
    }

    record CloseRangePacketFields(int display, int direction, int stance) {
    }

    record SkillAttackTiming(int hitDelayMs, int cooldownMs) {
    }

    record DamageProfile(int minDamage, int maxDamage, boolean magicAttack, boolean alwaysHit) {
    }

    private static int resolveSkillAttackDelayMillis(Skill skill) {
        if (skill == null) {
            return 0;
        }
        return Math.max(0, skill.getAnimationTime());
    }

    static SkillAttackTiming resolveSkillAttackTiming(Skill skill, String action, Character bot,
                                                      BasicAttackData fallbackAttackData) {
        int fallbackHitDelayMs = fallbackAttackData != null ? fallbackAttackData.hitDelayMs : defaultHitDelayMs(600);
        int fallbackCooldownMs = fallbackAttackData != null ? fallbackAttackData.cooldownMs : toCooldownMs(600);
        return resolveSkillAttackTiming(action, resolveSkillAttackDelayMillis(skill),
                resolveBaseWeaponAttackSpeed(bot), resolveWeaponAttackSpeed(bot),
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    static SkillAttackTiming resolveSkillAttackTiming(String action, int rawSkillDelayMs,
                                                      int baseWeaponAttackSpeed, int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int rawActionCooldownMs = provider.getBodyActionDurationMs(action);
        if (rawActionCooldownMs > 0) {
            int rawActionHitDelayMs = provider.getBodyActionAttackDelayMs(action, 0);
            int adjustedActionCooldownMs = adjustAttackDelayMillis(rawActionCooldownMs,
                    baseWeaponAttackSpeed, effectiveWeaponAttackSpeed);
            int adjustedActionHitDelayMs = rawActionHitDelayMs >= 0
                    ? adjustAttackDelayMillis(rawActionHitDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed)
                    : defaultHitDelayMs(adjustedActionCooldownMs);
            return new SkillAttackTiming(adjustedActionHitDelayMs,
                    Math.max(toCooldownMs(adjustedActionCooldownMs), fallbackCooldownMs));
        }

        return resolveSkillAttackTiming(rawSkillDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed,
                fallbackHitDelayMs, fallbackCooldownMs);
    }

    static SkillAttackTiming resolveSkillAttackTiming(int rawSkillDelayMs,
                                                      int baseWeaponAttackSpeed, int effectiveWeaponAttackSpeed,
                                                      int fallbackHitDelayMs, int fallbackCooldownMs) {
        if (rawSkillDelayMs <= 0) {
            return new SkillAttackTiming(fallbackHitDelayMs, fallbackCooldownMs);
        }

        int adjustedSkillDelayMs =
                adjustAttackDelayMillis(rawSkillDelayMs, baseWeaponAttackSpeed, effectiveWeaponAttackSpeed);
        return new SkillAttackTiming(defaultHitDelayMs(adjustedSkillDelayMs),
                Math.max(toCooldownMs(adjustedSkillDelayMs), fallbackCooldownMs));
    }

    private static int toCooldownMs(int attackDelayMillis) {
        return BotMovementManager.delayAfterCurrentTick(Math.max(0, attackDelayMillis));
    }

    private static int defaultHitDelayMs(int animationDelayMs) {
        if (animationDelayMs <= 0) {
            return 305;
        }
        return Math.max(0, animationDelayMs / 2);
    }

    private static int resolveWeaponAttackSpeed(Character bot) {
        return resolveEffectiveAttackSpeed(resolveBaseWeaponAttackSpeed(bot), bot);
    }

    private static int resolveBaseWeaponAttackSpeed(Character bot) {
        Item weapon = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return 4;
        }

        BotAttackDataProvider.NormalAttackProfile attackProfile =
                BotAttackDataProvider.getInstance().getNormalAttackProfile(weapon.getItemId());
        if (attackProfile == null) {
            return 4;
        }

        return attackProfile.getAttackSpeed();
    }

    private static int normalizeAttackSpeed(int attackSpeed) {
        if (attackSpeed <= 0) {
            return 4;
        }
        return attackSpeed;
    }

    private static int resolveEffectiveAttackSpeed(int baseAttackSpeed, Character bot) {
        int normalizedBaseSpeed = normalizeAttackSpeed(baseAttackSpeed);
        if (bot == null) {
            return normalizedBaseSpeed;
        }

        Integer booster = bot.getBuffedValue(BuffStat.BOOSTER);
        if (booster == null) {
            return normalizedBaseSpeed;
        }

        return Math.max(2, normalizedBaseSpeed + booster);
    }

    private static int adjustAttackDelayMillis(int baseDelayMillis, int baseAttackSpeed, int effectiveAttackSpeed) {
        if (baseDelayMillis <= 0) {
            return 0;
        }

        // Mirror OpenStory Char::get_attackdelay: effectiveDelay = rawDelay / (1.7 - speed/10).
        // baseDelayMillis is the raw WZ animation duration; divide by the effective speed factor
        // (which already incorporates any booster offset) to get the actual cooldown.
        float effectiveSpeedFactor = toAttackSpeedFactor(effectiveAttackSpeed);
        if (effectiveSpeedFactor <= 0f) {
            return baseDelayMillis;
        }

        return Math.max(1, Math.round(baseDelayMillis / effectiveSpeedFactor));
    }

    private static float toAttackSpeedFactor(int attackSpeed) {
        return 1.7f - (attackSpeed / 10f);
    }

    /** Applies monster WDEF/MDEF to raw [min, max] damage. Returns {adjMin, adjMax}. */
    private static int[] applyMonsterDefense(Character bot, Monster target, int minDmg, int maxDmg, boolean magic) {
        int D = Math.max(0, target.getLevel() - bot.getLevel());
        double adjMin, adjMax;
        if (magic) {
            int mdef = target.getMdef();
            adjMax = maxDmg - mdef * 0.5 * (1.0 + 0.01 * D);
            adjMin = minDmg - mdef * 0.6 * (1.0 + 0.01 * D);
        } else {
            int wdef = target.getWdef();
            double factor = 1.0 - 0.01 * D;
            adjMax = maxDmg * factor - wdef * 0.5;
            adjMin = minDmg * factor - wdef * 0.6;
        }
        int adjMinI = Math.max(1, (int) adjMin);
        int adjMaxI = Math.max(adjMinI, (int) adjMax);
        return new int[]{adjMinI, adjMaxI};
    }

    private static AbstractDealDamageHandler.AttackTarget makeTarget(Character bot, Monster monster, int hits,
                                                                     int minDmg, int maxDmg, int hitDelayMs,
                                                                     boolean magicAttack, boolean alwaysHit) {
        List<Integer> lines = alwaysHit
                ? BotCombatFormulaProvider.getInstance().rollDamageLines(hits, minDmg, maxDmg, 1.0d)
                : BotCombatFormulaProvider.getInstance().rollDamageLines(bot, monster, hits, minDmg, maxDmg, magicAttack);
        int normalizedHitDelay = Math.max(0, Math.min(Short.MAX_VALUE, hitDelayMs));
        return new AbstractDealDamageHandler.AttackTarget((short) normalizedHitDelay, lines);
    }

    static DamageProfile resolveDamageProfile(Character bot, int skillId, int skillLevel, boolean magicAttack) {
        Skill skill = skillId != 0 ? SkillFactory.getSkill(skillId) : null;
        StatEffect effect = skill != null && skillLevel > 0 ? skill.getEffect(skillLevel) : null;
        return resolveDamageProfile(bot, skillId, effect, magicAttack);
    }

    static DamageProfile resolveDamageProfile(Character bot, int skillId, StatEffect effect, boolean magicAttack) {
        if (effect != null && effect.getFixDamage() > 0) {
            int fixedDamage = Math.max(1, effect.getFixDamage());
            return new DamageProfile(fixedDamage, fixedDamage, magicAttack, true);
        }

        return magicAttack
                ? resolveMagicDamageProfile(bot, skillId, effect)
                : resolvePhysicalDamageProfile(bot, skillId, effect);
    }

    private static DamageProfile resolvePhysicalDamageProfile(Character bot, int skillId, StatEffect effect) {
        int watk = Math.max(0, bot.getTotalWatk());
        long maxDamage;
        long minDamage;

        if (skillId == Rogue.LUCKY_SEVEN
                || skillId == NightWalker.LUCKY_SEVEN
                || skillId == NightLord.TRIPLE_THROW
                || skillId == NightWalker.TRIPLE_THROW) {
            maxDamage = (long) (bot.getTotalLuk() * 5L) * (long) Math.ceil(watk / 100.0d);
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == DragonKnight.DRAGON_ROAR) {
            maxDamage = (long) (bot.getTotalStr() * 4L + bot.getTotalDex()) * (long) Math.ceil(watk / 100.0d);
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == NightLord.VENOMOUS_STAR || skillId == Shadower.VENOMOUS_STAB) {
            maxDamage = (long) Math.ceil((18.5d * (bot.getTotalStr() + bot.getTotalLuk()) + bot.getTotalDex() * 2.0d)
                    / 100.0d * bot.calculateMaxBaseDamage(watk));
            minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        } else if (skillId == Hermit.SHADOW_MESO && effect != null) {
            maxDamage = (long) Math.floor(effect.getMoneyCon() * 10.0d * 1.5d);
            minDamage = maxDamage;
        } else {
            maxDamage = bot.calculateMaxBaseDamage(watk);
            minDamage = bot.calculateMinBaseDamage(watk);
        }

        if (skillId != 0 && effect != null && skillId != Hermit.SHADOW_MESO) {
            int skillDamage = effect.getDamage();
            if (skillDamage > 0) {
                maxDamage = maxDamage * skillDamage / 100L;
                minDamage = minDamage * skillDamage / 100L;
            }
        }

        return normalizeDamageProfile(minDamage, maxDamage, false, false);
    }

    private static DamageProfile resolveMagicDamageProfile(Character bot, int skillId, StatEffect effect) {
        long maxDamage;
        if (skillId == Cleric.HEAL && effect != null) {
            maxDamage = Math.round((bot.getTotalInt() * 4.8d + bot.getTotalLuk() * 4.0d)
                    * bot.getTotalMagic() / 1000.0d);
            maxDamage = maxDamage * Math.max(0, effect.getHp()) / 100L;
        } else {
            maxDamage = bot.calculateMaxBaseMagicDamage(bot.getTotalMagic());
            maxDamage = applyMagicAmplification(bot, maxDamage);
            if (effect != null && effect.getMatk() > 0) {
                maxDamage *= effect.getMatk();
            }
        }

        // The server exposes a reliable magic max-damage formula, but not a paired min-damage helper.
        // Use a conservative range so bots do not collapse into fixed-value spell hits.
        long minDamage = Math.max(1L, Math.round(maxDamage * 0.8d));
        return normalizeDamageProfile(minDamage, maxDamage, true, false);
    }

    private static long applyMagicAmplification(Character bot, long baseDamage) {
        int amplificationSkillId = switch (bot.getJob()) {
            case IL_ARCHMAGE, IL_MAGE -> ILMage.ELEMENT_AMPLIFICATION;
            case FP_ARCHMAGE, FP_MAGE -> FPMage.ELEMENT_AMPLIFICATION;
            case BLAZEWIZARD3, BLAZEWIZARD4 -> BlazeWizard.ELEMENT_AMPLIFICATION;
            case EVAN7, EVAN8, EVAN9, EVAN10 -> Evan.MAGIC_AMPLIFICATION;
            default -> 0;
        };
        if (amplificationSkillId == 0) {
            return baseDamage;
        }

        Skill amplificationSkill = SkillFactory.getSkill(amplificationSkillId);
        if (amplificationSkill == null) {
            return baseDamage;
        }

        int amplificationLevel = bot.getSkillLevel(amplificationSkill);
        if (amplificationLevel <= 0) {
            return baseDamage;
        }

        StatEffect amplificationEffect = amplificationSkill.getEffect(amplificationLevel);
        return baseDamage * amplificationEffect.getY() / 100L;
    }

    private static DamageProfile normalizeDamageProfile(long minDamage, long maxDamage,
                                                        boolean magicAttack, boolean alwaysHit) {
        int normalizedMax = clampDamage(maxDamage);
        int normalizedMin = Math.min(normalizedMax, clampDamage(minDamage));
        return new DamageProfile(normalizedMin, normalizedMax, magicAttack, alwaysHit);
    }

    private static int clampDamage(long damage) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, damage));
    }

    static String describeDebugStats(BotEntry entry, Character bot) {
        Monster target = entry.grindTarget;
        if (target == null || !target.isAlive()) {
            target = findGrindTarget(bot);
        }

        AttackPlan plan = target != null ? planAttack(entry, bot, target) : null;
        String route = plan != null ? plan.route.name().toLowerCase() : determineBasicAttackRoute(bot).name().toLowerCase();
        int speed = plan != null ? plan.speed : resolveWeaponAttackSpeed(bot);
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
        entry.attackCooldownMs = Math.max(entry.attackCooldownMs, toCooldownMs(resolveSkillAttackDelayMillis(skill)));
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
