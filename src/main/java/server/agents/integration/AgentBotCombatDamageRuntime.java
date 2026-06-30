package server.agents.integration;

import client.BuffStat;
import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatTargetEligibilityPolicy;
import server.agents.capabilities.combat.AgentFallDamageCalculator;
import server.agents.capabilities.combat.AgentMobKnockbackPolicy;
import server.agents.capabilities.combat.data.AgentDefenseDataProvider;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;
import server.life.Monster;
import tools.PacketCreator;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

public final class AgentBotCombatDamageRuntime {
    private AgentBotCombatDamageRuntime() {
    }

    public static void applyMobHit(BotEntry entry, Character bot, Monster mob, AgentCombatConfig.Config config) {
        int dmg = AgentDefenseDataProvider.getInstance().rollPhysicalTouchDamage(bot, mob);
        AgentMobKnockbackPolicy.MobHitKnockback kb =
                AgentMobKnockbackPolicy.resolveMobHitKnockback(
                        bot.getPosition(), mob.getPosition(), config.KNOCKBACK_HSPEED, BotMovementManager.configuredTickMs());
        applyDamage(entry, bot, dmg, -1, mob.getId(), kb.direction(), kb.airVelX(), config);
    }

    public static void tickMobDamage(BotEntry entry, Character bot, AgentCombatConfig.Config config,
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

    public static void applyFallDamage(BotEntry entry, Character bot,
                                       float fallDistancePx,
                                       AgentCombatConfig.Config config) {
        if (bot.getHp() <= 0) return;
        if (AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry)) return;
        int dmg = AgentFallDamageCalculator.fallDamageFromDistance(fallDistancePx);
        if (dmg <= 0) return;
        int dirSign = AgentBotMovementStateRuntime.facingDirectionSign(entry);
        int airVelX = Math.round(-dirSign
                * AgentMobKnockbackPolicy.scaledOpenStoryStep(config.KNOCKBACK_HSPEED, BotMovementManager.configuredTickMs()));
        applyDamage(entry, bot, dmg, -3, 0, 0, airVelX, config);
    }

    private static void applyDamage(BotEntry entry, Character bot, int dmg,
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
                    BotMovementManager.delayAfterCurrentTick(config.MOB_HIT_COOLDOWN_MS));
            AgentBotCombatAlertRuntime.markAlerted(entry);
            return;
        }

        bot.addMPHPAndTriggerAutopot(-dmg, 0);

        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(damageFrom, monsterId, bot.getId(), dmg, 0,
                        broadcastDirection, false, 0, false, 0, 0, 0), false);

        AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(
                entry,
                BotMovementManager.delayAfterCurrentTick(config.MOB_HIT_COOLDOWN_MS));
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
            BotPhysicsEngine.applyAirKnockback(entry, bot, knockbackAirVelX);
        } else {
            BotPhysicsEngine.beginKnockback(entry, bot, botPos,
                    -AgentMobKnockbackPolicy.scaledOpenStoryStep(
                            config.KNOCKBACK_VFORCE, BotMovementManager.configuredTickMs()),
                    knockbackAirVelX);
        }
        BotMovementManager.broadcastMovement(entry);
    }
}
