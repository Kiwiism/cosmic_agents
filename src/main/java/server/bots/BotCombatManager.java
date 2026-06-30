package server.bots;

import server.agents.capabilities.combat.AgentCombatConfig;

import client.Character;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.life.Monster;

public class BotCombatManager {
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

    public static String describeDebugStats(BotEntry entry, Character bot) {
        return AgentBotCombatReportRuntime.debugStatsReport(entry, bot);
    }

}



