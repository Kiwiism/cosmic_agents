package server.bots.pq;

import client.Character;
import server.bots.BotEntry;

/**
 * Single call-site for all per-map party-quest bot automation.
 * BotManager calls {@link #tick} once per bot tick; each PQ class handles its own map range.
 */
public final class BotPqHooks {

    private BotPqHooks() {}

    public static void tick(BotEntry entry, Character bot, Character owner) {
        BotKpqStage1.tick(entry, bot, owner);
        BotKpqStage5.tick(entry, bot);
    }

    /**
     * Returns true when the bot should stand idle at an NPC and skip normal AI.
     */
    public static boolean isNpcLocked(BotEntry entry) {
        return BotKpqStage1.isNpcLocked(entry);
    }

    /** Returns true if the bot is in a PQ map that requires grind mode (KPQ stage 1). */
    public static boolean requiresGrind(BotEntry entry, Character bot) {
        return bot.getMapId() == BotKpqStage1.KPQ_STAGE1_MAP;
    }

    /** True once the bot no longer needs coupons — suppress coupon loot. */
    public static boolean shouldSkipCouponLoot(BotEntry entry) {
        return BotKpqStage1.shouldSkipCouponLoot(entry);
    }

    /**
     * True during KPQ stage 1 grinding when a nearby coupon drop has been located.
     * Caller should treat this as a soft movement hint, not a hard nav override — the bot
     * should still fight mobs opportunistically and drift toward the coupon only when idle.
     */
    public static boolean isCouponSeeking(BotEntry entry) {
        return BotKpqStage1.isCouponSeeking(entry);
    }

    /** Returns true if the bot is in a PQ map that should default to follow mode (KPQ stages 2-5). */
    public static boolean requiresFollow(BotEntry entry, Character bot) {
        int mapId = bot.getMapId();
        return mapId >= 103000801 && mapId <= 103000805;
    }
}
