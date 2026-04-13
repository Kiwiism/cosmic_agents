package server.bots.pq;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import scripting.event.EventInstanceManager;
import server.bots.BotChatManager;
import server.bots.BotEntry;
import server.life.NPC;
import server.maps.MapItem;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KPQ Stage 1 automation — bot navigates to Cloto, gets assigned a coupon target,
 * grinds until it has enough, exchanges them at Cloto, then delivers the pass to owner.
 */
final class BotKpqStage1 {

    static final int KPQ_STAGE1_MAP = 103000800;
    private static final int NPC_CLOTO    = 9020001;
    private static final int ITEM_COUPON  = 4001007;
    private static final int ITEM_PASS    = 4001008;

    // Question index (1-9) → required coupon count; index 0 unused.
    //What Level do you need to become a Magician? (8)
    //What Level do you need to become a Bowman? (10)
    //What Level do you need to become a Thief? (10)
    //What Level do you need to become a Warrior? (10)
    //How much EXP is required from lvl 1 to lvl 2? (15)
    //How much INT is required to become a Magician? (20)
    //How much DEX is required to become a Thief? (25)
    //How much DEX is required to become a Bowman? (25)
    //How much STR is required to become a Warrior? (35)
    private static final int[] ANSWERS = {0, 8, 10, 10, 10, 15, 20, 25, 25, 35};

    static final int IDLE         = 0;
    static final int FIRST_WALK   = 1;  // walking to Cloto for assignment
    static final int FIRST_WAIT   = 2;  // standing at Cloto; brief pause then assign
    static final int GRINDING     = 3;  // farming coupons
    static final int SECOND_WALK  = 4;  // walking to Cloto to exchange
    static final int SECOND_WAIT  = 5;  // at Cloto; brief pause then exchange
    static final int DELIVERING   = 6;  // walking to owner with pass
    static final int DONE         = 7;  // finished

    private static final int NEAR_NPC_PX    = 80;
    private static final int NEAR_OWNER_PX  = 80;
    private static final long WAIT_MS       = 1800;

    // ── entry point ─────────────────────────────────────────────────────────

    static void tick(BotEntry entry, Character bot, Character owner) {
        int mapId = bot.getMapId();
        if (mapId != KPQ_STAGE1_MAP) {
            if (entry.kpq.state != IDLE) reset(entry);
            return;
        }
        switch (entry.kpq.state) {
            case IDLE        -> tickIdle(entry, bot);
            case FIRST_WALK  -> tickFirstWalk(entry, bot);
            case FIRST_WAIT  -> tickFirstWait(entry, bot);
            case GRINDING    -> tickGrinding(entry, bot);
            case SECOND_WALK -> tickSecondWalk(entry, bot);
            case SECOND_WAIT -> tickSecondWait(entry, bot);
            case DELIVERING  -> tickDelivering(entry, bot, owner);
        }
    }

    /** True once the bot no longer needs coupons (exchange done or delivering). */
    static boolean shouldSkipCouponLoot(BotEntry entry) {
        return entry.kpq.state >= SECOND_WALK;
    }

    /**
     * True during GRINDING when a nearby coupon has been located.
     * This is a soft nav hint — the bot should still fight mobs opportunistically
     * and only drift toward the coupon when idle.
     */
    static boolean isCouponSeeking(BotEntry entry) {
        return entry.kpq.state == GRINDING && entry.kpq.navTarget != null;
    }

    static boolean isNpcLocked(BotEntry entry) {
        return entry.kpq.state == FIRST_WAIT || entry.kpq.state == SECOND_WAIT;
    }

    // ── state ticks ─────────────────────────────────────────────────────────

    private static void tickIdle(BotEntry entry, Character bot) {
        to(entry, FIRST_WALK);
        entry.kpq.navTarget = getNpcPos(bot);
    }

    private static void tickFirstWalk(BotEntry entry, Character bot) {
        Point npcPos = getNpcPos(bot);
        if (npcPos == null) return;
        entry.kpq.navTarget = npcPos;
        if (near(bot, npcPos, NEAR_NPC_PX)) {
            entry.kpq.navTarget = null;
            entry.kpq.waitUntilMs = System.currentTimeMillis() + WAIT_MS;
            to(entry, FIRST_WAIT);
        }
    }

    private static void tickFirstWait(BotEntry entry, Character bot) {
        if (System.currentTimeMillis() < entry.kpq.waitUntilMs) return;

        EventInstanceManager eim = bot.getEventInstance();
        int question = (eim != null) ? eim.gridCheck(bot) : -1;
        if (question == -1) {
            question = ThreadLocalRandom.current().nextInt(1, ANSWERS.length);
            if (eim != null) eim.gridInsert(bot, question);
        }
        int target = (question < ANSWERS.length) ? ANSWERS[question] : ANSWERS[1];
        entry.kpq.couponTarget = target;
        if (target >= 25) {
            BotChatManager.queueBotSay(entry, "I need " + target + ", smh");
        } else {
            BotChatManager.queueBotSay(entry, "I need " + target + ", Let's go!");
        }
        to(entry, GRINDING);
    }

    private static final int COUPON_SEEK_RANGE = 600;

    private static void tickGrinding(BotEntry entry, Character bot) {
        int have = bot.getItemQuantity(ITEM_COUPON, false);
        int need = entry.kpq.couponTarget;

        // Drop excess coupons so teammates can use them.
        if (have > need) {
            int excess = have - need;
            Inventory etc = bot.getInventory(InventoryType.ETC);
            Item coupon = etc.findById(ITEM_COUPON);
            if (coupon != null) {
                InventoryManipulator.drop(bot.getClient(), InventoryType.ETC, coupon.getPosition(), (short) excess);
            }
            have = need;
        }

        if (have >= need) {
            BotChatManager.queueBotSay(entry, "Got " + need + "!");
            to(entry, SECOND_WALK);
            entry.kpq.navTarget = getNpcPos(bot);
            return;
        }

        // Seek coupon drops on the ground if any are nearby.
        MapItem groundCoupon = findNearestDrop(bot, ITEM_COUPON, COUPON_SEEK_RANGE);
        entry.kpq.navTarget = (groundCoupon != null) ? groundCoupon.getPosition() : null;

        // Report at every 5-coupon milestone
        int milestone = (have / 5) * 5;
        if (milestone > entry.kpq.lastReportedCoupons) {
            entry.kpq.lastReportedCoupons = milestone;
            BotChatManager.queueBotSay(entry, have + " / " + need);
        }
    }

    private static MapItem findNearestDrop(Character bot, int itemId, int range) {
        Point pos = bot.getPosition();
        MapItem nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (MapItem drop : bot.getMap().getDroppedItems()) {
            if (drop.getItemId() != itemId || drop.isPickedUp()) continue;
            if (!drop.canBePickedBy(bot)) continue;
            Point dp = drop.getPosition();
            if (Math.abs(dp.x - pos.x) > range || Math.abs(dp.y - pos.y) > range) continue;
            double dist = pos.distanceSq(dp);
            if (dist < bestDist) { bestDist = dist; nearest = drop; }
        }
        return nearest;
    }

    private static void tickSecondWalk(BotEntry entry, Character bot) {
        Point npcPos = getNpcPos(bot);
        if (npcPos == null) return;
        entry.kpq.navTarget = npcPos;
        if (near(bot, npcPos, NEAR_NPC_PX)) {
            entry.kpq.navTarget = null;
            entry.kpq.waitUntilMs = System.currentTimeMillis() + WAIT_MS;
            to(entry, SECOND_WAIT);
        }
    }

    private static void tickSecondWait(BotEntry entry, Character bot) {
        if (System.currentTimeMillis() < entry.kpq.waitUntilMs) return;

        int target = entry.kpq.couponTarget;
        if (bot.getItemQuantity(ITEM_COUPON, false) >= target) {
            InventoryManipulator.removeById(bot.getClient(), InventoryType.ETC, ITEM_COUPON, target, false, false);
            InventoryManipulator.addById(bot.getClient(), ITEM_PASS, (short) 1);
            EventInstanceManager eim = bot.getEventInstance();
            if (eim != null) eim.gridInsert(bot, 0);
        }
        BotChatManager.queueBotSay(entry, "Got my pass! Bringing it to you.");
        to(entry, DELIVERING);
    }

    private static void tickDelivering(BotEntry entry, Character bot, Character owner) {
        Point ownerPos = owner.getPosition();
        entry.kpq.navTarget = ownerPos;
        if (near(bot, ownerPos, NEAR_OWNER_PX)) {
            entry.kpq.navTarget = null;
            Inventory etc = bot.getInventory(InventoryType.ETC);
            // Drop leftover coupons for teammates before delivering the pass.
            Item leftover = etc.findById(ITEM_COUPON);
            if (leftover != null) {
                InventoryManipulator.drop(bot.getClient(), InventoryType.ETC, leftover.getPosition(), leftover.getQuantity());
            }
            Item pass = etc.findById(ITEM_PASS);
            if (pass != null) {
                InventoryManipulator.drop(bot.getClient(), InventoryType.ETC, pass.getPosition(), (short) 1);
            }
            BotChatManager.queueBotSay(entry, "Here's your pass!");
            to(entry, DONE);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static void to(BotEntry entry, int newState) {
        entry.kpq.state = newState;
        entry.kpq.lastReportedCoupons = 0;
    }

    private static void reset(BotEntry entry) {
        entry.kpq.state               = IDLE;
        entry.kpq.couponTarget        = -1;
        entry.kpq.waitUntilMs         = 0;
        entry.kpq.lastReportedCoupons = 0;
        entry.kpq.navTarget           = null;
    }

    private static Point getNpcPos(Character bot) {
        NPC npc = bot.getMap().getNPCById(NPC_CLOTO);
        return (npc != null) ? npc.getPosition() : null;
    }

    private static boolean near(Character bot, Point target, int dist) {
        Point p = bot.getPosition();
        return Math.abs(p.x - target.x) <= dist && Math.abs(p.y - target.y) <= dist;
    }
}
