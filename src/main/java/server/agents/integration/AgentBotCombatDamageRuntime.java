package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import server.agents.capabilities.movement.AgentMovementTimers;

import client.BuffStat;
import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatTargetEligibilityPolicy;
import server.agents.capabilities.combat.AgentFallDamageCalculator;
import server.agents.capabilities.combat.AgentMobKnockbackPolicy;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentKnockbackMovementService;
import server.agents.capabilities.combat.data.AgentDefenseDataProvider;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import tools.PacketCreator;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

public final class AgentBotCombatDamageRuntime {
    private AgentBotCombatDamageRuntime() {
    }

    public static void applyMobHit(AgentRuntimeEntry entry, Character bot, Monster mob, AgentCombatConfig.Config config) {
        int dmg = AgentDefenseDataProvider.getInstance().rollPhysicalTouchDamage(bot, mob);
        AgentMobKnockbackPolicy.MobHitKnockback kb =
                AgentMobKnockbackPolicy.resolveMobHitKnockback(
                        bot.getPosition(), mob.getPosition(), config.KNOCKBACK_HSPEED, AgentMovementPhysicsConfig.configuredMovementTickMs());
        applyDamage(entry, bot, dmg, -1, mob.getId(), kb.direction(), kb.airVelX(), config);
    }

    public static void tickMobDamage(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config,
                                     IntUnaryOperator cooldownTickDown) {
        Point botPos = bot.getPosition();
        try {
            if (AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry)) {
                AgentBotCombatCooldownStateRuntime.tickMobHitCooldown(entry, cooldownTickDown);
                return;
            }
            if (bot.getHp() <= 0) return;

            for (Monster mob : bot.getMap().getAllMonsters()) {
                if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(mob)) continue;
                if (AgentBotMobTouchRuntime.isMobTouchingAgent(entry, bot, mob, config.MOB_TOUCH_SWEEP_HEIGHT)) {
                    applyMobHit(entry, bot, mob, config);
                    return;
                }
            }
        } finally {
            AgentBotMobTouchRuntime.rememberMobTouchCheck(entry, bot, botPos);
        }
    }

    public static void applyFallDamage(AgentRuntimeEntry entry, Character bot,
                                       float fallDistancePx,
                                       AgentCombatConfig.Config config) {
        if (bot.getHp() <= 0) return;
        if (AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry)) return;
        int dmg = AgentFallDamageCalculator.fallDamageFromDistance(fallDistancePx);
        if (dmg <= 0) return;
        int dirSign = AgentBotMovementStateRuntime.facingDirectionSign(entry);
        int airVelX = Math.round(-dirSign
                * AgentMobKnockbackPolicy.scaledOpenStoryStep(config.KNOCKBACK_HSPEED, AgentMovementPhysicsConfig.configuredMovementTickMs()));
        applyDamage(entry, bot, dmg, -3, 0, 0, airVelX, config);
    }

    private static void applyDamage(AgentRuntimeEntry entry, Character bot, int dmg,
                                    int damageFrom, int monsterId,
                                    int broadcastDirection, int knockbackAirVelX,
                                    AgentCombatConfig.Config config) {
        Point botPos = bot.getPosition();

        if (dmg <= 0) {
            bot.getMap().broadcastMessage(bot,
                    PacketCreator.damagePlayer(damageFrom, monsterId, bot.getId(), 0, 0,
                            broadcastDirection, false, 0, false, 0, 0, 0), false);
            AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(
                    entry,
                    AgentMovementTimers.delayAfterCurrentTick(config.MOB_HIT_COOLDOWN_MS));
            AgentBotCombatAlertRuntime.markAlerted(entry);
            return;
        }

        bot.addMPHPAndTriggerAutopot(-dmg, 0);

        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(damageFrom, monsterId, bot.getId(), dmg, 0,
                        broadcastDirection, false, 0, false, 0, 0, 0), false);

        AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(config.MOB_HIT_COOLDOWN_MS));
        AgentBotCombatAlertRuntime.markAlerted(entry);

        if (bot.getHp() <= 0) {
            AgentBotCombatDeathRuntime.enterDeadState(entry, bot, true, config);
            return;
        }

        if (!AgentMobKnockbackPolicy.shouldApplyMobKnockback(
                AgentBotMovementStateRuntime.climbing(entry),
                bot.getHp(),
                bot.getBuffedValue(BuffStat.STANCE),
                ThreadLocalRandom.current().nextFloat())) {
            return;
        }

        AgentBotCombatActionStateRuntime.clearActionState(entry);
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            AgentKnockbackMovementService.applyAirKnockback(entry, bot, knockbackAirVelX);
        } else {
            AgentKnockbackMovementService.beginKnockback(entry, bot, botPos,
                    -AgentMobKnockbackPolicy.scaledOpenStoryStep(
                            config.KNOCKBACK_VFORCE, AgentMovementPhysicsConfig.configuredMovementTickMs()),
                    knockbackAirVelX);
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
