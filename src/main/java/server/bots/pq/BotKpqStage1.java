package server.bots.pq;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import scripting.event.EventInstanceManager;
import server.bots.BotChatManager;
import server.bots.BotEntry;
import server.bots.BotScript;
import server.bots.BotScriptContext;
import server.bots.BotScriptStep;
import server.life.NPC;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KPQ Stage 1 automation expressed as a generic bot script:
 * move -> wait -> assign -> grind -> move -> wait -> exchange -> follow/drop.
 */
final class BotKpqStage1 {

    static final int KPQ_STAGE1_MAP = 103000800;
    private static final int NPC_CLOTO = 9020001;
    private static final int ITEM_COUPON = 4001007;
    private static final int ITEM_PASS = 4001008;

    // Question index (1-9) -> required coupon count; index 0 unused.
    private static final int[] ANSWERS = {0, 8, 10, 10, 10, 15, 20, 25, 25, 35};

    static final int IDLE = 0;
    static final int FIRST_WALK = 1;
    static final int FIRST_WAIT = 2;
    static final int GRINDING = 3;
    static final int SECOND_WALK = 4;
    static final int SECOND_WAIT = 5;
    static final int DELIVERING = 6;
    static final int DONE = 7;

    private static final int NEAR_NPC_PX = 80;
    private static final int NEAR_OWNER_PX = 80;
    private static final long WAIT_MS = 1800;

    private static final BotScript SCRIPT = new BotScript() {
        private final List<BotScriptStep> steps = List.of(
                moveToCloto(FIRST_WALK),
                BotScriptStep.of(ctx -> {
                    ctx.entry.kpq.state = FIRST_WAIT;
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, BotScriptContext::waitDone),
                BotScriptStep.action(BotKpqStage1::assignCouponTarget),
                BotScriptStep.of(ctx -> {
                    ctx.entry.kpq.state = GRINDING;
                    ctx.queueGrind();
                }, BotKpqStage1::tickCouponGrinding, BotKpqStage1::hasRequiredCoupons),
                moveToCloto(SECOND_WALK),
                BotScriptStep.of(ctx -> {
                    ctx.entry.kpq.state = SECOND_WAIT;
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, BotScriptContext::waitDone),
                BotScriptStep.action(BotKpqStage1::exchangeCoupons),
                BotScriptStep.of(BotKpqStage1::queuePassDelivery, null, BotScriptContext::tasksDone),
                BotScriptStep.action(ctx -> {
                    BotChatManager.queueBotSay(ctx.entry, "Here's your pass!");
                    ctx.entry.kpq.state = DONE;
                })
        );

        @Override
        public String id() {
            return "kpq-stage-1";
        }

        @Override
        public boolean applies(BotEntry entry, Character bot, Character owner) {
            if (bot.getMapId() != KPQ_STAGE1_MAP) {
                if (entry.kpq.state != IDLE) {
                    reset(entry);
                }
                return false;
            }
            return entry.kpq.state != DONE;
        }

        @Override
        public List<BotScriptStep> steps() {
            return steps;
        }
    };

    static BotScript script() {
        return SCRIPT;
    }

    static boolean shouldSkipCouponLoot(BotEntry entry) {
        return entry.kpq.state >= SECOND_WALK;
    }

    static boolean isNpcLocked(BotEntry entry) {
        return entry.kpq.state == FIRST_WAIT || entry.kpq.state == SECOND_WAIT;
    }

    private static BotScriptStep moveToCloto(int state) {
        return BotScriptStep.of(ctx -> {
            ctx.entry.kpq.state = state;
            queueMoveToCloto(ctx);
        }, BotKpqStage1::tickMoveToCloto, ctx -> {
            Point npcPos = getNpcPos(ctx.bot);
            return npcPos != null && near(ctx.bot, npcPos, NEAR_NPC_PX) && ctx.tasksDone();
        });
    }

    private static void tickMoveToCloto(BotScriptContext ctx) {
        if (ctx.tasksDone()) {
            queueMoveToCloto(ctx);
        }
    }

    private static void queueMoveToCloto(BotScriptContext ctx) {
        Point npcPos = getNpcPos(ctx.bot);
        if (npcPos != null && !near(ctx.bot, npcPos, NEAR_NPC_PX)) {
            ctx.queueMoveTo(npcPos, false);
        }
    }

    private static void assignCouponTarget(BotScriptContext ctx) {
        EventInstanceManager eim = ctx.bot.getEventInstance();
        int question = (eim != null) ? eim.gridCheck(ctx.bot) : -1;
        if (question == -1) {
            question = ThreadLocalRandom.current().nextInt(1, ANSWERS.length);
            if (eim != null) eim.gridInsert(ctx.bot, question);
        }
        int target = (question < ANSWERS.length) ? ANSWERS[question] : ANSWERS[1];
        ctx.entry.kpq.couponTarget = target;
        if (target >= 25) {
            BotChatManager.queueBotSay(ctx.entry, "I need " + target + ", smh");
        } else {
            BotChatManager.queueBotSay(ctx.entry, "I need " + target + ", Let's go!");
        }
    }

    private static void tickCouponGrinding(BotScriptContext ctx) {
        int have = ctx.bot.getItemQuantity(ITEM_COUPON, false);
        int need = ctx.entry.kpq.couponTarget;

        if (have > need) {
            ctx.manager.issueDropItem(ctx.entry, InventoryType.ETC, ITEM_COUPON, (short) (have - need));
            have = need;
        }

        int milestone = (have / 5) * 5;
        if (milestone > ctx.entry.kpq.lastReportedCoupons) {
            ctx.entry.kpq.lastReportedCoupons = milestone;
            BotChatManager.queueBotSay(ctx.entry, have + " / " + need);
        }
    }

    private static boolean hasRequiredCoupons(BotScriptContext ctx) {
        int need = ctx.entry.kpq.couponTarget;
        if (need <= 0 || ctx.bot.getItemQuantity(ITEM_COUPON, false) < need) {
            return false;
        }
        BotChatManager.queueBotSay(ctx.entry, "Got " + need + "!");
        return true;
    }

    private static void exchangeCoupons(BotScriptContext ctx) {
        int target = ctx.entry.kpq.couponTarget;
        if (ctx.bot.getItemQuantity(ITEM_COUPON, false) >= target) {
            InventoryManipulator.removeById(ctx.bot.getClient(), InventoryType.ETC, ITEM_COUPON, target, false, false);
            InventoryManipulator.addById(ctx.bot.getClient(), ITEM_PASS, (short) 1);
            EventInstanceManager eim = ctx.bot.getEventInstance();
            if (eim != null) eim.gridInsert(ctx.bot, 0);
        }
        BotChatManager.queueBotSay(ctx.entry, "Got my pass! Bringing it to you.");
    }

    private static void queuePassDelivery(BotScriptContext ctx) {
        ctx.entry.kpq.state = DELIVERING;
        ctx.queueFollowUntilNearOwner(NEAR_OWNER_PX);
        ctx.queueDrop(InventoryType.ETC, ITEM_COUPON, (short) 0);
        ctx.queueDrop(InventoryType.ETC, ITEM_PASS, (short) 1);
    }

    private static void reset(BotEntry entry) {
        entry.kpq.state = IDLE;
        entry.kpq.couponTarget = -1;
        entry.kpq.waitUntilMs = 0;
        entry.kpq.lastReportedCoupons = 0;
        entry.script.reset(null);
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
