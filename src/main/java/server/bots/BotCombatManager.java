package server.bots;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackPlanScoringPolicy;
import server.agents.capabilities.combat.AgentAttackPlanTieBreakPolicy;
import server.agents.capabilities.combat.AgentBasicAttackPlanner;
import server.agents.capabilities.combat.AgentCombatAttackExecutionPolicy;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatAmmoPolicy;
import server.agents.capabilities.combat.AgentFallDamageCalculator;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.capabilities.combat.AgentCombatWeaponPolicy;
import server.agents.capabilities.combat.AgentCombatSkillHitboxPolicy;
import server.agents.capabilities.combat.AgentCombatHitCounter;
import server.agents.capabilities.combat.AgentCombatHitboxIntersection;
import server.agents.capabilities.combat.AgentCombatImmediateTargetPolicy;
import server.agents.capabilities.combat.AgentCombatGrindTargetPolicy;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentCombatSkillUsePolicy;
import server.agents.capabilities.combat.AgentCombatSupportPolicy;
import server.agents.capabilities.combat.AgentCombatTargetEligibilityPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.capabilities.combat.AgentMobTouchPolicy;
import server.agents.capabilities.combat.AgentMobKnockbackPolicy;
import server.agents.capabilities.combat.AgentProjectileHitbox;
import server.agents.capabilities.combat.AgentScoredGrindTarget;
import server.agents.capabilities.combat.AgentSkillAttackPlanner;
import server.agents.capabilities.combat.AgentGrindTargetGroup;
import server.agents.capabilities.combat.AgentSupportSpecialMovePacketBuilder;

import server.agents.runtime.AgentPerformanceMonitor;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.BuffStat;
import client.Character;
import client.Client;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.Bowmaster;
import constants.skills.Buccaneer;
import constants.skills.Corsair;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.GM;
import constants.skills.Marksman;
import constants.skills.Priest;
import constants.skills.Spearman;
import constants.skills.ThunderBreaker;
import constants.skills.WhiteKnight;
import io.netty.buffer.Unpooled;
import net.PacketHandler;
import net.PacketProcessor;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.server.channel.handlers.AbstractDealDamageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.StatEffect;
import server.agents.capabilities.combat.data.AgentAttackDataProvider;
import server.agents.capabilities.combat.data.AgentDefenseDataProvider;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatBuffStateRuntime;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;
import server.agents.integration.AgentBotCombatRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMobTouchStateRuntime;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSkillBuffDebugStateRuntime;
import server.combat.CombatFormulaProvider;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapObject;
import server.maps.MapObjectType;
import server.maps.MapleMap;
import tools.PacketCreator;
import tools.Pair;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

public class BotCombatManager {
    private static final Logger log = LoggerFactory.getLogger(BotCombatManager.class);
    private static final long UNREACHABLE_GRAPH_COST = Long.MAX_VALUE / 4;

    static final class AttackPlan extends AgentAttackPlan {
        AttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                   AgentAttackRoute route, int display, int direction, int rangedDirection, int stance, int speed,
                   int hitDelayMs, int cooldownMs, WeaponType damageWeaponType) {
            super(skillId, skillLevel, numDamage, hitBox, targets, route, display, direction, rangedDirection,
                    stance, speed, hitDelayMs, cooldownMs, damageWeaponType);
        }
    }

    public static AgentCombatConfig.Config cfg = AgentCombatConfig.cfg;

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    static void tickMobDamage(BotEntry entry, Character bot) {
        Point botPos = bot.getPosition();
        try {
            if (AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry)) {
                AgentBotCombatCooldownStateRuntime.tickMobHitCooldown(entry, BotMovementManager::tickDown);
                return;
            }
            if (bot.getHp() <= 0) return;

            for (Monster mob : bot.getMap().getAllMonsters()) {
                if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(mob)) continue;
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
        int dmg = AgentDefenseDataProvider.getInstance().rollPhysicalTouchDamage(bot, mob);
        AgentMobKnockbackPolicy.MobHitKnockback kb =
                AgentMobKnockbackPolicy.resolveMobHitKnockback(
                        bot.getPosition(), mob.getPosition(), cfg.KNOCKBACK_HSPEED, BotMovementManager.cfg.TICK_MS);
        applyDamage(entry, bot, dmg, -1, mob.getId(), kb.direction(), kb.airVelX());
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
        if (bot.getHp() <= 0) return;
        if (AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry)) return; // damage invincibility window
        int dmg = AgentFallDamageCalculator.fallDamageFromDistance(fallDistancePx);
        if (dmg <= 0) return;
        int dirSign = AgentBotMovementStateRuntime.facingDirectionSign(entry);
        int airVelX = Math.round(-dirSign
                * AgentMobKnockbackPolicy.scaledOpenStoryStep(cfg.KNOCKBACK_HSPEED, BotMovementManager.cfg.TICK_MS));
        applyDamage(entry, bot, dmg, -3, 0, 0, airVelX);
    }

    /**
     * Core damage application: HP loss, DAMAGE_PLAYER broadcast, alert pose, knockback.
     * Shared by mob-touch (damageFrom=-1) and fall (damageFrom=-3). Call via helpers
     * {@link #applyMobHit} / {@link #applyFallDamage} so magic numbers stay out of call sites.
     *
     * @param broadcastDirection direction byte in DAMAGE_PLAYER packet (0 or 1).
     *                           Mob-hit: derived from attacker side.
     *                           Fall:    always 0 (observed in real-client samples).
     * @param knockbackAirVelX   signed horizontal impulse for physics knockback (px/tick-step).
     */
    private static void applyDamage(BotEntry entry, Character bot, int dmg,
                                    int damageFrom, int monsterId,
                                    int broadcastDirection, int knockbackAirVelX) {
        AgentCombatConfig.Config cc = BotCombatManager.cfg;
        Point botPos = bot.getPosition();

        if (dmg <= 0) {
            bot.getMap().broadcastMessage(bot,
                    PacketCreator.damagePlayer(damageFrom, monsterId, bot.getId(), 0, 0,
                            broadcastDirection, false, 0, false, 0, 0, 0), false);
            AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(
                    entry,
                    BotMovementManager.delayAfterCurrentTick(cc.MOB_HIT_COOLDOWN_MS));
            markAlerted(entry);
            return;
        }

        bot.addMPHPAndTriggerAutopot(-dmg, 0);

        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(damageFrom, monsterId, bot.getId(), dmg, 0,
                        broadcastDirection, false, 0, false, 0, 0, 0), false);

        AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(
                entry,
                BotMovementManager.delayAfterCurrentTick(cc.MOB_HIT_COOLDOWN_MS));
        markAlerted(entry);

        if (bot.getHp() <= 0) {
            enterDeadState(entry, bot, true);
            return;
        }

        if (!AgentMobKnockbackPolicy.shouldApplyMobKnockback(
                AgentBotMovementStateRuntime.climbing(entry),
                bot.getHp(),
                bot.getBuffedValue(BuffStat.STANCE),
                ThreadLocalRandom.current().nextFloat())) {
            return;
        }

        clearActionState(entry);
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            BotPhysicsEngine.applyAirKnockback(entry, bot, knockbackAirVelX);
        } else {
            BotPhysicsEngine.beginKnockback(entry, bot, botPos,
                    -AgentMobKnockbackPolicy.scaledOpenStoryStep(
                            cfg.KNOCKBACK_VFORCE, BotMovementManager.cfg.TICK_MS),
                    knockbackAirVelX);
        }
        BotMovementManager.broadcastMovement(entry);
    }

    static void enterDeadState(BotEntry entry, Character bot, boolean announceDeath) {
        clearActionState(entry);
        BotPhysicsEngine.markDead(entry, bot);
        BotMovementManager.broadcastMovement(entry);
        AgentBotDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), cfg.BOT_DEAD_MS);
        if (announceDeath) {
            AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatDeathReplies()));
        }
    }

    private static void clearActionState(BotEntry entry) {
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        BotMovementManager.clearNavigationState(entry);
        AgentBotMovementBroadcastStateRuntime.invalidate(entry);
    }

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        int skillSignature = AgentCombatSkillClassifier.skillCacheSignature(bot);
        if (AgentBotCombatSkillCacheStateRuntime.matches(
                entry, bot.getJob().getId(), bot.getLevel(), skillSignature)) {
            return;
        }

        AgentBotCombatSkillCacheStateRuntime.reset(entry, bot.getJob().getId(), bot.getLevel(), skillSignature);

        int bestAtkHits = 0;
        int bestAtkPriority = Integer.MIN_VALUE;
        int bestAtkDamage = Integer.MIN_VALUE;
        long bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            int atk = AgentCombatHitCounter.effectiveHitCount(fx);
            int mobs = fx.getMobCount();
            AgentCombatSkillClassifier.SkillCacheBucket cacheBucket =
                    AgentCombatSkillClassifier.classifySkillCacheBucket(skill, fx);

            if (AgentCombatSkillClassifier.shouldStopCacheScanAfterHealSkill(skill)) {
                if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_HEAL) {
                    AgentBotCombatSkillCacheStateRuntime.setHealSkillId(entry, skill.getId());
                }
                continue;  // not an attack skill; offensive use against undead handled in tickSupportHealing
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_ATTACK) {
                AgentBotCombatSkillCacheStateRuntime.addAttackSkillId(entry, skill.getId());
                if (mobs >= 2) {
                    long score = AgentCombatSkillClassifier.aoeSkillScore(fx, atk, mobs);
                    if (score > bestAoeScore) {
                        bestAoeScore = score;
                        AgentBotCombatSkillCacheStateRuntime.setAoeSkill(entry, skill.getId(), mobs);
                    }
                } else if (AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(bot, skill, fx, atk,
                        bestAtkHits, bestAtkPriority, bestAtkDamage,
                        AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry))) {
                    bestAtkHits = atk;
                    bestAtkPriority = AgentCombatSkillClassifier.singleTargetSkillPriority(bot, skill);
                    bestAtkDamage = fx.getDamage();
                    AgentBotCombatSkillCacheStateRuntime.setAttackSkillId(entry, skill.getId());
                }
                continue;
            }

            if (cacheBucket == AgentCombatSkillClassifier.SkillCacheBucket.SUMMON) {
                // Own bucket, not rebuffable — see BotEntry.summonSkillIds.
                AgentBotCombatSkillCacheStateRuntime.addSummonSkillId(entry, skill.getId());
                continue;
            }

            if (cacheBucket != AgentCombatSkillClassifier.SkillCacheBucket.SUPPORT_BUFF) continue;
            AgentBotCombatSkillCacheStateRuntime.addBuffSkillId(entry, skill.getId());
            AgentBotCombatBuffStateRuntime.ensureNextBuffAt(entry, skill.getId(), 0L);
        }
    }

    static void tickBuffs(BotEntry entry, Character bot) {
        AgentCombatSupportPolicy.SkillBuffTickDecision tickDecision =
                AgentCombatSupportPolicy.skillBuffTickDecision(
                        AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentBotCombatBuffStateRuntime.skillBuffsEnabled(entry),
                        AgentBotModeStateRuntime.following(entry),
                        AgentBotModeStateRuntime.grinding(entry),
                        AgentBotCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        if (tickDecision != AgentCombatSupportPolicy.SkillBuffTickDecision.READY) {
            if (tickDecision.legacyDebugSummary() != null) {
                noteSkillBuffDecision(entry, tickDecision.legacyDebugSummary());
            }
            return;
        }
        boolean hasLivingMobs = bot.getMap().getAllMonsters().stream().anyMatch(Monster::isAlive);
        if (AgentCombatSupportPolicy.shouldSkipSkillBuffsWithoutLivingMobs(hasLivingMobs)) return;

        long now = System.currentTimeMillis();
        if (trySupportBuff(entry, bot, now)) {
            return;
        }

        for (int skillId : AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (now < AgentBotCombatBuffStateRuntime.nextBuffAt(entry, skillId)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            if (!AgentCombatSkillClassifier.isActiveSupportSkill(skill, fx)
                    || AgentCombatSkillClassifier.isBuffBlacklisted(skill.getId())) {
                continue;
            }
            if (castSupportSkill(entry, bot, skill, fx, now)) {
                return;
            }
        }
        noteSkillBuffDecision(entry, AgentCombatSupportPolicy.allSkillBuffsActiveOrOnCooldownSummary());
    }

    /**
     * Healing is the cleric bot's top priority: runs before any attack decision (see BotManager tick)
     * and casts whenever the bot itself OR any nearby party member is below
     * {@link AgentCombatConfig.Config#SUPPORT_HEAL_TARGET_RATIO}. There is no decision-side cooldown — the only
     * throttle is the Agent combat cooldown state, which we set from the skill's animation timing so
     * consecutive casts match what a legit client would send (~600ms between Heal packets per the
     * captured monitored-packets-cleric-heal-only.log reference).
     *
     * <p>The cast packet is broadcast even when no undead targets are in range so other clients see
     * the heal animation play (matches real player behaviour when Heal is pressed with no mob in range).
     */
    static boolean tickSupportHealing(BotEntry entry, Character bot) {
        int healSkillId = AgentBotCombatSkillCacheStateRuntime.healSkillId(entry);
        if (!AgentCombatSupportPolicy.shouldTickSupportHealing(
                AgentBotCombatCooldownStateRuntime.blocksGroundedAttack(entry, AgentBotMovementStateRuntime.inAir(entry)),
                AgentBotCombatBuffStateRuntime.supportHealsEnabled(entry),
                AgentBotModeStateRuntime.following(entry),
                AgentBotModeStateRuntime.grinding(entry),
                healSkillId,
                bot.skillIsCooling(healSkillId))) {
            return false;
        }

        Skill skill = SkillFactory.getSkill(healSkillId);
        int lvl = bot.getSkillLevel(skill);
        if (lvl <= 0) return false;
        StatEffect fx = skill.getEffect(lvl);

        // Decision range MUST match the skill's actual WZ hitbox. If we decide to heal based on a
        // looser SUPPORT_RANGE but fx.applyTo() iterates only members inside the heal bbox, a party
        // member outside the bbox but inside SUPPORT_RANGE would never receive HP and the bot would
        // re-cast every tick forever. Anchor both self- and party-checks to fx.calculateBoundingBox.
        Rectangle healBounds = fx.hasBoundingBox()
                ? fx.calculateBoundingBox(bot.getPosition(), bot.isFacingLeft())
                : null;
        boolean selfNeedsHeal = AgentCombatSupportPolicy.needsHeal(bot, cfg.SUPPORT_HEAL_TARGET_RATIO);
        boolean partyNeedsHeal = selfNeedsHeal || AgentCombatSupportPolicy.hasPartyMemberInBoundsNeedingHeal(
                bot,
                healBounds,
                cfg.SUPPORT_RANGE,
                cfg.SUPPORT_VERTICAL_RANGE,
                cfg.SUPPORT_HEAL_TARGET_RATIO);
        List<Monster> undeadTargets = getUndeadMobsInHealRange(bot, fx, healBounds);
        if (!AgentCombatSupportPolicy.shouldCastSupportHeal(partyNeedsHeal, !undeadTargets.isEmpty())) return false;

        // Jump-heal: when following and the leader has pulled ahead, kick a diagonal jump toward
        // them right before the cast. The top guard already permits casts while inAir, so the
        // heal animation plays mid-flight instead of forcing a planted stand-and-cast.
        boolean jumpHealing = false;
        if (AgentBotModeStateRuntime.following(entry)
                && AgentBotMovementStateRuntime.grounded(entry)
                && AgentBotMovementStateRuntime.notClimbing(entry)
                && cfg.JUMP_HEAL_LEADER_AHEAD_PX > 0) {
            Character anchor = BotManager.getInstance().resolveFollowAnchor(entry, AgentBotRuntimeIdentityRuntime.owner(entry));
            if (anchor != null && anchor != bot && anchor.getMap() == bot.getMap()) {
                int dx = anchor.getPosition().x - bot.getPosition().x;
                if (Math.abs(dx) >= cfg.JUMP_HEAL_LEADER_AHEAD_PX) {
                    BotMovementManager.initiateJump(entry, bot, dx);
                    jumpHealing = true;
                }
            }
        }

        if (!fx.canPaySkillCost(bot) || !fx.applyTo(bot)) return false;

        long now = System.currentTimeMillis();
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, lvl,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, skillTiming.cooldownMs());
        if (partyNeedsHeal && fx.getCooldown() > 0) {
            bot.addCooldown(healSkillId, now, fx.getCooldown() * 1000L);
        }

        // Always send the attack/cast packet so the animation plays even when there are no undead
        // to hit — the packet carries an empty targets map in that case, which is what a real client
        // does when a player presses Heal with no mob in range.
        sendHealAttack(healSkillId, lvl, bot, undeadTargets, fallbackAttackData, skillTiming);
        markAlerted(entry);
        AgentBotCombatCooldownStateRuntime.maxMoveWindow(entry, cfg.HEAL_MOVE_WINDOW_MS);
        if (!jumpHealing) {
            // Stop walk-in-place: broadcast STAND→ALERT immediately on the heal tick.
            // Skipped on jump-heal — initiateJump already broadcast the airborne stance and
            // zeroing moveDir here would cancel the diagonal air-steering toward the leader.
            AgentBotMovementStateRuntime.clearMoveDirection(entry);
            BotMovementManager.broadcastMovement(entry);
        }
        return true;
    }

    /**
     * Sends a skill attack packet targeting undead mobs with Heal damage.
     * Called after fx.applyTo() has already handled the party heal and MP cost,
     * so we build the AttackInfo directly rather than going through attackMonster()
     * (which would re-check MP via canUseSkill and fail).
     */
    private static void sendHealAttack(int healSkillId, int lvl, Character bot,
            List<Monster> undeadTargets,
            AgentAttackExecutionProvider.BasicAttackData fallbackAttackData,
            AgentAttackExecutionProvider.SkillAttackTiming skillTiming) {
        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(bot, healSkillId);
        // N in Russt's target multiplier is caster + damaged targets. When no undead are in range
        // the damage profile is unused (numAttacked=0) but we still pass 1 to avoid a divide-by-zero
        // surprise if the profile gets reused elsewhere.
        int healTargetCount = Math.max(1, undeadTargets.size() + 1);
        CombatFormulaProvider.DamageProfile damageProfile = CombatFormulaProvider.getInstance()
                .resolveDamageProfile(bot, healSkillId, lvl, true, healTargetCount);
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = healSkillId;
        attack.skilllevel = lvl;
        attack.numDamage = 1;
        attack.numAttacked = undeadTargets.size();
        attack.numAttackedAndDamage = (undeadTargets.size() << 4) | 1;
        attack.speed = fallbackAttackData.speed();
        // Real cleric Heal packet (captured in monitored-packets-cleric-heal-only.log) encodes
        // the direction byte as bodyActionId("alert2") = 41 (0x29) so other clients render the
        // caster in the magic-casting "alert2" pose rather than the idle-frame default. The
        // stance byte is the shared facing mask used by every attack-plan builder.
        boolean facingLeft = bot.isFacingLeft();
        AgentAttackExecutionProvider.CloseRangePacketFields castFields =
                AgentAttackExecutionProvider.mimicCloseRangePacketFields("alert2", "alert2", facingLeft);
        attack.display = castFields.display();
        attack.direction = castFields.bodyActionId();
        attack.stance = AgentAttackExecutionProvider.attackPacketStance(facingLeft);
        attack.rangedirection = AgentAttackExecutionProvider.attackPacketStance(facingLeft);
        attack.ranged = false;
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();
        for (Monster target : undeadTargets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(
                            bot, target, 1, healSkillId, damageProfile, skillTiming.hitDelayMs()));
        }
        AgentAttackExecutionProvider.applyAttackRoute(route, attack, bot);
    }

    private static List<Monster> getUndeadMobsInHealRange(Character bot, StatEffect fx, Rectangle bounds) {
        if (bounds == null) {
            return AgentCombatTargetSelector.collectUndeadMobsInHealRange(null, List.of(), fx.getMobCount());
        }
        List<MapObject> objects = bot.getMap().getMapObjectsInRect(bounds, Arrays.asList(MapObjectType.MONSTER));
        return AgentCombatTargetSelector.collectUndeadMobsInHealRange(bounds, objects, fx.getMobCount());
    }

    static Monster findGrindTarget(Character bot) {
        return findGrindTarget(null, bot);
    }

    /** Returns the most convenient reachable target (deterministic — closest/best score wins). */
    static Monster findGrindTarget(BotEntry entry, Character bot) {
        long startedAt = System.nanoTime();
        try {
            Point botPos = bot.getPosition();
            double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
            Foothold botFoothold = findGroundFoothold(botPos, bot);
            List<Monster> candidates = aliveMonstersInRange(bot, botPos, rangeSq);
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
            List<Monster> candidates = aliveMonstersInRange(bot, botPos, rangeSq);
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
            List<Monster> candidates = aliveMonstersInRange(bot, botPos, range * range);
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
                    candidate -> aoeClusterBonus(entry, candidate, candidates));
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
        long targetCost = targetPresentAndAlive && hasRuntimeContext && !immediateProjectileTarget && graphAvailable
                ? graphTargetCost(graphContext, target)
                : UNREACHABLE_GRAPH_COST;
        return AgentCombatGrindTargetPolicy.isReachableGrindTarget(
                targetPresentAndAlive,
                hasRuntimeContext,
                immediateProjectileTarget,
                graphAvailable,
                targetCost,
                UNREACHABLE_GRAPH_COST);
    }

    static AttackPlan planAttack(BotEntry entry, Character bot, Monster target) {
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
        AgentBasicAttackPlanner.BasicAttackSelection selection = AgentBasicAttackPlanner.selectBasicAttack(
                target,
                candidate -> AgentAttackExecutionProvider.buildBasicAttackData(bot, candidate.getPosition()),
                (candidate, hitBox) -> resolveEffectivePrimary(bot, candidate, hitBox),
                AgentCombatHitboxIntersection::intersectsMonster,
                candidate -> findReachableOnOppositeFacing(bot, candidate));
        if (selection == null) {
            return null;
        }
        AgentAttackExecutionProvider.BasicAttackData basicAttackData = selection.attackData();
        Monster effective = selection.target();
        int numDamage = AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, basicAttackData.route());
        return new AttackPlan(0, 0, numDamage, basicAttackData.hitBox(), List.of(effective), basicAttackData.route(),
                basicAttackData.display(), basicAttackData.direction(), basicAttackData.rangedDirection(), basicAttackData.stance(),
                basicAttackData.speed(), basicAttackData.hitDelayMs(), basicAttackData.cooldownMs(),
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(
                        0, AgentAttackExecutionProvider.getEquippedWeaponType(bot), basicAttackData.action()));
    }

    private static Monster findReachableOnOppositeFacing(Character bot, Monster originalTarget) {
        if (bot == null) {
            return null;
        }
        return AgentCombatTargetSelector.findReachableOnOppositeFacing(
                bot.getPosition(),
                originalTarget,
                mirroredPos -> AgentAttackExecutionProvider.buildBasicAttackData(bot, mirroredPos).hitBox(),
                hitBox -> resolveEffectivePrimary(bot, originalTarget, hitBox));
    }

    static boolean isTargetInAttackRange(AttackPlan attackPlan, Character bot, Monster target) {
        if (attackPlan == null) {
            return false;
        }
        return AgentCombatRangePolicy.isTargetInAttackRange(
                attackPlan.hasHitBox() ? attackPlan.hitBox : null,
                target,
                bot.getPosition(),
                target.getPosition());
    }

    static boolean canUseAttackPlanNow(BotEntry entry, WeaponType weaponType, AttackPlan attackPlan) {
        if (entry == null || attackPlan == null) {
            return false;
        }
        if (AgentBotMovementStateRuntime.grounded(entry)) {
            return true;
        }
        return AgentCombatRangePolicy.canUseAttackPlanNow(false, weaponType, attackPlan.route);
    }

    static boolean isTargetJumpable(AgentMovementProfile movementProfile, boolean closeRangeRoute, Point botPos, Point targetPos) {
        return AgentCombatRangePolicy.isTargetJumpable(movementProfile, closeRangeRoute, botPos, targetPos,
                BotPhysicsEngine.calculateMaxJumpHeight(movementProfile));
    }

    static boolean isTargetJumpable(boolean closeRangeRoute, Point botPos, Point targetPos) {
        return isTargetJumpable(AgentMovementProfile.base(), closeRangeRoute, botPos, targetPos);
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        AgentCombatAttackExecutionPolicy.AttackExecutionReadiness readiness =
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentBotAmmoStateRuntime.noAmmo(entry),
                        attackPlan.skillId,
                        () -> AgentCombatSkillUsePolicy.canPaySkillCost(
                                bot, attackPlan.skillId, attackPlan.skillLevel),
                        () -> canUseAttackPlanNow(entry, AgentAttackExecutionProvider.getEquippedWeaponType(bot), attackPlan));
        if (readiness != AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.READY) {
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
        attack.ranged = attackPlan.route == AgentAttackRoute.RANGED;
        CombatFormulaProvider.DamageProfile damageProfile = CombatFormulaProvider.getInstance().resolveDamageProfile(
                bot, attackPlan.skillId, attackPlan.skillLevel,
                attackPlan.route == AgentAttackRoute.MAGIC, attackPlan.damageWeaponType);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(bot, target, attackPlan.numDamage,
                            attackPlan.skillId, damageProfile, attackPlan.hitDelayMs));
        }

        AgentAttackExecutionProvider.applyAttackRoute(attackPlan.route, attack, bot);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, attackPlan.cooldownMs);
        rememberAttackFacing(entry, attackPlan.stance);
        markAlerted(entry);
    }

    static void rememberAttackFacing(BotEntry entry, int attackPacketStance) {
        AgentBotMovementStateRuntime.setFacingDirection(entry,
                AgentAttackExecutionProvider.facingDirFromAttackPacketStance(attackPacketStance));
        BotPhysicsEngine.syncCharacterState(entry);
    }

    static void tickActionLock(BotEntry entry) {
        if (AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            AgentBotCombatCooldownStateRuntime.tickAttackCooldown(entry, BotMovementManager::tickDown);
        } else if (AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry)) {
            // Movement window: animation done, bot may walk but not attack yet.
            AgentBotCombatCooldownStateRuntime.tickMoveWindow(entry, BotMovementManager::tickDown);
        }
    }

    // Matches maplestory-wasm CharLook::set_alerted(5000): called on attack, skill cast, and
    // damage taken. Always an absolute reset to now+5s (never additive), mirroring TimedBool::set_for.
    private static final long ALERT_DURATION_MS = 5000L;

    static void markAlerted(BotEntry entry) {
        AgentBotCombatCooldownStateRuntime.setAlertedUntilMs(entry, System.currentTimeMillis() + ALERT_DURATION_MS);
        scheduleAlertReset(entry);
    }

    // Ensures the bot broadcasts a fresh STAND packet when the alert timer expires, even if
    // it has stopped moving in the meantime (otherwise the last-sent ALERT wire stance sticks).
    // Self-reschedules if markAlerted extended the deadline while we were waiting.
    private static void scheduleAlertReset(BotEntry entry) {
        if (AgentBotCombatCooldownStateRuntime.alertResetScheduled(entry)) return;
        AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, true);
        long delay = Math.max(50L, AgentBotCombatCooldownStateRuntime.alertedUntilMs(entry) - System.currentTimeMillis() + 100L);
        AgentBotCombatRuntime.afterDelay(delay, () -> {
            long now = System.currentTimeMillis();
            if (now < AgentBotCombatCooldownStateRuntime.alertedUntilMs(entry)) {
                AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
                scheduleAlertReset(entry);
                return;
            }
            AgentBotCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);
            try {
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                if (bot != null) bot.broadcastStance();
            } catch (Throwable ignored) {}
        });
    }

    private static AttackPlan planSkillAttack(BotEntry entry, Character bot, Monster primaryTarget, int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        int skillLevel = skill == null ? 0 : bot.getSkillLevel(skill);
        StatEffect effect = skill == null || skillLevel <= 0 ? null : skill.getEffect(skillLevel);
        AgentSkillAttackPlanner.SkillAttackReadiness readiness = AgentSkillAttackPlanner.skillAttackReadiness(
                skillId,
                skillId != 0 && bot.skillIsCooling(skillId),
                skill != null,
                skillLevel,
                () -> effect.canPaySkillCost(bot),
                () -> AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                        skillId, AgentAttackExecutionProvider.getEquippedWeaponType(bot)));
        if (readiness != AgentSkillAttackPlanner.SkillAttackReadiness.READY) {
            return null;
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(bot, skillId);
        // Ammo gate: ranged skills with bulletCount need that many arrows/stars/bullets in
        // the bot's USE inventory. canPaySkillCost only covers MP/HP. countAmmo returns
        // MAX_VALUE for non-ammo weapons and while Soul Arrow / Shadow Claw are active.
        // Avenger / Iron Arrow set bulletConsume (e.g. 3 for Avenger) for ammo cost without
        // changing the visible projectile count, so use the larger of the two. Shadow
        // Partner doubles the actual consume (see RangedAttackHandler.bulletConsume *= 2).
        if (AgentSkillAttackPlanner.skillAmmoReadiness(
                effect.getBulletCount(),
                effect.getBulletConsume(),
                AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route),
                route,
                () -> AgentCombatAmmoCounter.countAmmo(bot, weaponType))
                != AgentSkillAttackPlanner.SkillAmmoReadiness.READY) {
            return null;
        }
        // Resolve the animated action once up front: weapon-action sampling is random, so the
        // reach hitbox and the broadcast packet must share the same swing (a close skill without
        // its own lt/rb gates the hit on this action's afterimage box).
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel, weaponType);
        if (AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(skillId)) {
            primaryTarget = resolveStrikePointPrimaryByBasicWeapon(bot, primaryTarget, route);
        }
        Rectangle hitBox = AgentCombatSkillHitboxPolicy.calculateSkillHitBox(
                effect, bot, primaryTarget, route, skillId, action);
        if (hitBox == null) {
            return null;
        }

        boolean strikePointAnchored = AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(skillId);
        Monster preSelectionPrimaryTarget = primaryTarget;
        AgentSkillAttackPlanner.SkillPrimaryTargetSelection targetSelection =
                AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                        strikePointAnchored,
                        preSelectionPrimaryTarget,
                        hitBox,
                        () -> AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(
                                bot, preSelectionPrimaryTarget, route),
                        (candidate, candidateHitBox) -> resolveEffectivePrimary(bot, candidate, candidateHitBox),
                        AgentCombatHitboxIntersection::intersectsMonster);
        if (targetSelection == null) {
            return null;
        }
        primaryTarget = targetSelection.target();

        int attackCount = AgentCombatHitCounter.effectiveHitCount(effect)
                * AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route);
        if (!AgentAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), primaryTarget.getPosition())) {
            return null;
        }
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, primaryTarget.getPosition());
        AgentAttackDataProvider.AttackAnimationSpec attackSpec = AgentAttackDataProvider.getInstance().getBasicAttackSpec(weaponType);
        String fallbackAction = attackSpec.primaryAction();
        AgentSkillAttackPlanner.SkillAttackPacketFields packetFields =
                AgentSkillAttackPlanner.resolveSkillAttackPacketFields(
                        route, weaponType, bot.getPosition(), primaryTarget.getPosition(), action, fallbackAction);
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        List<Monster> targets = AgentCombatTargetSelector.collectTargetsInHitBox(
                primaryTarget, hitBox, Math.max(1, effect.getMobCount()), bot.getMap().getAllMonsters());
        if (skillId == DragonKnight.DRAGON_ROAR
                && !AgentCombatSupportPolicy.canUseDragonRoarPlan(
                bot, targets.size(),
                AgentCombatSupportPolicy.hasNearbyHealSkillAlly(
                        bot, cfg.SUPPORT_RANGE, cfg.SUPPORT_VERTICAL_RANGE))) {
            return null;
        }
        return new AttackPlan(skillId, skillLevel, attackCount, hitBox, targets,
                route, packetFields.display(),
                packetFields.direction(), packetFields.rangedDirection(),
                packetFields.stance(),
                fallbackAttackData.speed(), skillTiming.hitDelayMs(), skillTiming.cooldownMs(),
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(skillId, weaponType, action));
    }

    /**
     * Returns the number of damage lines a skill fires per target.
     * Uses the larger of attackCount and bulletCount from skill data —
     * claw skills like Lucky Seven store their projectile count in bulletCount.
     */
    // Shadow Partner (Hermit / NightWalker / DualBlade book) doubles the per-mob damage
    // line count for ranged attacks: the client packs `numDamage * 2` into
    // numAttackedAndDamage and the second half of each mob's lines are rolled at half
    // damage. The server validates this in AbstractDealDamageHandler (`maxattack * 2`
    // autoban headroom) and RangedAttackHandler (`bulletConsume * 2`). Bots inherit the
    // multiplier automatically once the buff lands on them (future admin command).
    // Melee and magic routes are left untouched here — Shadow Partner's melee/magic
    // doubling for thief skills (e.g. Triple Throw is ranged-claw, so it covers itself)
    // can be enabled per-skill later if needed.
    /**
     * True iff the AoE skill's expected total damage (damage% × hits × targets) beats
     * the bot's best single-target option (best of configured attack skill or basic 100%).
     * Used to gate AoE selection when only a small cluster is in range.
     */
    private static boolean beatsSingleTargetScore(Character bot, BotEntry entry, StatEffect aoeEffect,
                                                  int aoeAttackCount, int targetCount) {
        int aoeDamage = Math.max(0, aoeEffect.getDamage());
        long singleScore = 100L; // basic attack: 100% damage × 1 line
        int attackSkillId = AgentBotCombatSkillCacheStateRuntime.attackSkillId(entry);
        if (attackSkillId != 0) {
            Skill skill = SkillFactory.getSkill(attackSkillId);
            int level = skill == null ? 0 : bot.getSkillLevel(skill);
            if (level > 0) {
                StatEffect fx = skill.getEffect(level);
                if (fx != null) {
                    singleScore = Math.max(singleScore,
                            AgentCombatScoringPolicy.bestSingleTargetScore(
                                    fx.getDamage(), AgentCombatHitCounter.effectiveHitCount(fx)));
                }
            }
        }
        return AgentCombatScoringPolicy.aoeBeatsSingleTargetScore(aoeDamage, aoeAttackCount, targetCount,
                singleScore);
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

    private static List<Monster> aliveMonstersInRange(Character bot, Point botPos, double rangeSq) {
        return AgentCombatTargetSelector.aliveMonstersInRange(bot.getMap().getAllMonsters(), botPos, rangeSq);
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
                candidate -> aoeClusterBonus(entry, candidate, candidates));
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
                        - aoeClusterBonus(entry, candidate, candidates),
                group -> graphPathCost(context.graph(), context.map(), context.startPos(), context.startRegionId(),
                        group.bestMonster().getPosition(), group.regionId(), context.profile()),
                group -> grindRegionOccupancyPenalty(context, bot, group.regionId()),
                UNREACHABLE_GRAPH_COST);
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

    // Cluster-density bonus: when the bot has an AoE skill, bias target selection toward
    // mobs that anchor a cluster. The single-skill DPS scorer already prefers AoE plans
    // over basic ones on the same target (selectBestAttackPlan), but it never sees the
    // alternative cluster if target selection passed it over for a closer/lone mob.
    //
    // Returns a non-negative bonus that is subtracted from the candidate's localScore
    // (lower score wins). Capped by the AoE skill's mobCount-1 so a 6-mob skill on a
    // pile of 10 mobs doesn't crater scores past the natural distance/foothold penalties.
    private static long aoeClusterBonus(BotEntry entry, Monster target, List<Monster> candidates) {
        return AgentCombatScoringPolicy.legacyAoeClusterBonus(target, candidates,
                entry != null && AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                entry == null ? 0 : AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
    }

    /** True iff the bot has a multi-mob AoE skill but its chosen plan is single-target with room to hit more. */
    static boolean isAoeBotSingleTargeting(BotEntry entry, AttackPlan plan) {
        return entry != null && plan != null && AgentCombatScoringPolicy.isAoeSingleTargeting(
                plan.skillId,
                plan.targets.size(),
                AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentBotCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
    }

    /** Live mobs within AoE cluster radius of the anchor (including itself), capped at the skill's mobCount. */
    static int aoeClusterSize(BotEntry entry, Character bot, Monster anchor) {
        if (entry == null || bot == null || anchor == null
                || bot.getMap() == null || anchor.getPosition() == null) {
            return 0;
        }
        return AgentCombatScoringPolicy.legacyCappedAoeClusterSize(anchor, bot.getMap().getAllMonsters(),
                AgentBotCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentBotCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
    }

    // AoE positioning: target selection (aoeClusterBonus) steers the bot toward a cluster, but the
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

    static boolean isMobTouchingBot(BotEntry entry, Character bot, Monster mob) {
        Rectangle botBounds = getBotTouchBounds(entry, bot);
        Rectangle mobBounds = AgentMobHitboxProvider.getInstance().getMobBounds(mob);
        if (mobBounds == null) {
            return false;
        }
        return AgentMobTouchPolicy.lowerHalfIntersects(mobBounds, botBounds);
    }

    static Rectangle getBotTouchBounds(BotEntry entry, Character bot) {
        Point currentPos = bot.getPosition();
        Point previousPos = currentPos;
        Point rememberedPos = AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, bot.getMapId());
        if (rememberedPos != null) {
            previousPos = rememberedPos;
        }

        return AgentMobTouchPolicy.botTouchSweepBounds(previousPos, currentPos, cfg.MOB_TOUCH_SWEEP_HEIGHT);
    }

    private static Rectangle inclusiveRectangle(int left, int top, int right, int bottom) {
        return AgentMobTouchPolicy.inclusiveRectangle(left, top, right, bottom);
    }

    private static void rememberMobTouchCheck(BotEntry entry, Character bot, Point position) {
        if (entry == null || bot == null || position == null) {
            return;
        }

        AgentBotMobTouchStateRuntime.rememberCheck(entry, position, bot.getMapId());
    }

    private static Monster resolveStrikePointPrimaryByBasicWeapon(Character bot, Monster fallback, AgentAttackRoute route) {
        if (bot == null) {
            return fallback;
        }
        // Strike-point-anchored skills center their bbox on the target, so hitbox
        // intersection is not a reach gate. MAGIC route remains ungated to preserve legacy behavior.
        return AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                bot.getPosition(),
                fallback,
                route,
                facingLeft -> AgentCombatRangePolicy.basicWeaponReachRect(bot, facingLeft, route),
                hitBox -> resolveEffectivePrimary(bot, fallback, hitBox));
    }

    static Monster resolveEffectivePrimary(Character bot, Monster fallback, Rectangle hitBox) {
        Point botPos = bot.getPosition();
        return AgentCombatTargetSelector.resolveEffectivePrimary(botPos, fallback, hitBox, bot.getMap().getAllMonsters());
    }

    static Monster findClosestAliveMonster(Character bot, double maxRangeSq) {
        Point botPos = bot.getPosition();
        return AgentCombatTargetSelector.findClosestAliveMonster(bot.getMap().getAllMonsters(), botPos, maxRangeSq);
    }

    private static void noteSkillBuffDecision(BotEntry entry, String summary) {
        AgentBotSkillBuffDebugStateRuntime.rememberAction(entry, System.currentTimeMillis(), summary);
    }

    public static List<String> getSkillBuffDebugLines(BotEntry entry, Character bot) {
        return AgentBotCombatReportRuntime.skillBuffDebugLines(entry, bot);
    }

    public static String describeDebugStats(BotEntry entry, Character bot) {
        Monster target = AgentBotGrindTargetStateRuntime.target(entry);
        if (target == null || !target.isAlive()) {
            target = findGrindTarget(bot);
        }

        AttackPlan plan = target != null ? planAttack(entry, bot, target) : null;
        String route = plan != null ? plan.route.name().toLowerCase() : AgentAttackExecutionProvider.determineBasicAttackRoute(bot).name().toLowerCase();
        int speed = plan != null ? plan.speed : AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition()).speed();
        double cooldownSeconds = (plan != null ? plan.cooldownMs : 0) / 1000.0;
        double remainingSeconds = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry) / 1000.0;
        String targetName = target != null ? target.getName() : "none";

        return AgentCombatDialogueReporter.debugStatsReport(
                route, speed, cooldownSeconds, remainingSeconds,
                BotMovementManager.cfg.TICK_MS, BotManager.cfg.AI_TICK_MS, targetName);
    }

    private static boolean trySupportBuff(BotEntry entry, Character bot, long now) {
        for (int skillId : AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (!AgentCombatSupportPolicy.shouldConsiderSupportBuff(
                    AgentCombatSkillClassifier.isPartySupportSkill(skillId),
                    bot.skillIsCooling(skillId),
                    AgentBotCombatBuffStateRuntime.supportBuffOnCooldown(entry, skillId, now))) {
                continue;
            }

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) {
                continue;
            }

            StatEffect fx = skill.getEffect(lvl);
            if (!AgentCombatSupportPolicy.hasNearbyPartyMemberMissingBuff(
                    bot, fx, cfg.SUPPORT_RANGE, cfg.SUPPORT_VERTICAL_RANGE)) {
                continue;
            }

            if (castSupportSkill(entry, bot, skill, fx, now)) {
                AgentBotCombatBuffStateRuntime.setNextSupportBuffAt(
                        entry, skillId, now + cfg.SUPPORT_REBUFF_CD_MS);
                return true;
            }
        }

        return false;
    }

    private static boolean castSupportSkill(BotEntry entry, Character bot, Skill skill, StatEffect fx, long now) {
        int skillLevel = bot.getSkillLevel(skill);
        AgentCombatSupportPolicy.SupportCastReadiness readiness =
                AgentCombatSupportPolicy.supportCastReadiness(skillLevel, bot.isAlive(), () -> fx.canPaySkillCost(bot));
        String readinessSummary = readiness.legacyDebugSummary(
                AgentCombatDialogueReporter.combatSkillLabel(skill.getId()));
        if (readinessSummary != null) {
            noteSkillBuffDecision(entry, readinessSummary);
            return false;
        }
        if (!dispatchSupportSpecialMove(bot, skill, skillLevel)) {
            noteSkillBuffDecision(entry, AgentCombatSupportPolicy.supportSpecialMoveFailedSummary(
                    AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
            return false;
        }

        long dur = fx.getDuration();
        if (dur > 0) {
            AgentBotCombatBuffStateRuntime.setNextBuffAt(
                    entry, skill.getId(), AgentCombatSupportPolicy.nextSupportBuffRefreshAt(now, dur));
        }
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry,
                AgentCombatSupportPolicy.supportCastCooldownMs(skillTiming.cooldownMs(), skill.getAnimationTime()));
        markAlerted(entry);
        noteSkillBuffDecision(entry, AgentCombatSupportPolicy.supportCastSummary(
                AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
        return true;
    }

    private static boolean dispatchSupportSpecialMove(Character bot, Skill skill, int skillLevel) {
        Client client = bot.getClient();
        if (client == null) {
            return false;
        }

        byte[] packetBytes = AgentSupportSpecialMovePacketBuilder.build(
                bot,
                skill.getId(),
                skillLevel,
                net.server.Server.getInstance().getCurrentTimestamp());
        InPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(packetBytes));
        short packetId = packet.readShort();
        PacketHandler handler = PacketProcessor.getProcessor(bot.getWorld(), client.getChannel()).getHandler(packetId);
        if (handler == null || !handler.validateState(client)) {
            return false;
        }

        handler.handlePacket(packet, client);
        return true;
    }

    /** Returns true if the bot's weapon type requires projectile ammo. */
    /**
     * Periodically checks ammo for ranged bots. Piggybacks on the pot-check timer.
     * Warns at AMMO_LOW_WARN, stops grinding and follows owner at 0.
     */
    static void tickAmmoCheck(BotEntry entry, Character bot) {
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        boolean mage = weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF;
        boolean rangedAmmoWeapon = AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType);
        int mpPotionCount = 0;
        int ammo = Integer.MAX_VALUE;
        if (mage) {
            for (Item item : bot.getInventory(InventoryType.USE).list()) {
                if (item.getQuantity() <= 0) {
                    continue;
                }
                StatEffect effect = BotInventoryManager.itemEffect(item.getItemId());
                if (effect == null || !effect.getStatups().isEmpty()) {
                    continue;
                }
                if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                    mpPotionCount += item.getQuantity();
                    if (mpPotionCount >= BotManager.cfg.POT_LOW_WARN) {
                        break;
                    }
                }
            }
        } else if (rangedAmmoWeapon) {
            ammo = AgentCombatAmmoCounter.countAmmo(bot, weaponType);
        }

        AgentCombatAmmoPolicy.AmmoCheckDecision decision = AgentCombatAmmoPolicy.ammoCheckDecision(
                mage,
                rangedAmmoWeapon,
                mpPotionCount,
                ammo,
                cfg.AMMO_LOW_WARN,
                AgentBotAmmoStateRuntime.ammoWarnSent(entry),
                AgentBotAmmoStateRuntime.noAmmo(entry));
        switch (decision) {
            case CLEAR_WARNING_STATE -> AgentBotAmmoStateRuntime.clearAmmoWarningState(entry);
            case MAGE_NO_MP_POTS -> {
                AgentBotAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentBotModeStateRuntime.grinding(entry)) {
                    BotManager.getInstance().issueFollowOwner(entry);
                    AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatMpPotsOutReplies()));
                }
            }
            case PROJECTILE_LOW_AMMO -> {
                AgentBotAmmoStateRuntime.setAmmoWarnSent(entry, true);
                AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatAmmoLowReplies()));
            }
            case PROJECTILE_NO_AMMO -> {
                AgentBotAmmoStateRuntime.setNoAmmo(entry, true);
                if (AgentBotModeStateRuntime.grinding(entry)) {
                    BotManager.getInstance().issueFollowOwner(entry);
                    AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatAmmoOutReplies()));
                }
            }
            case NO_CHANGE -> {
            }
        }
    }

}
