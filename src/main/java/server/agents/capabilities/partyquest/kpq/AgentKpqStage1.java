package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import scripting.event.EventInstanceManager;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.plans.AgentScript;
import server.agents.plans.AgentScriptContext;
import server.agents.plans.AgentScriptStep;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
import server.life.NPC;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * KPQ Stage 1 automation expressed as a generic bot script:
 * move -> wait -> assign -> grind -> move -> wait -> exchange -> follow/drop.
 */
public final class AgentKpqStage1 {

    public static final int KPQ_STAGE1_MAP = 103000800;
    private static final int NPC_CLOTO = 9020001;
    private static final int ITEM_COUPON = 4001007;
    private static final int ITEM_PASS = 4001008;

    // Question index (1-9) -> required coupon count; index 0 unused.
    private static final int[] ANSWERS = {0, 8, 10, 10, 10, 15, 20, 25, 25, 35};

    public static final int IDLE = 0;
    static final int FIRST_WALK = 1;
    static final int FIRST_WAIT = 2;
    public static final int GRINDING = 3;
    static final int SECOND_WALK = 4;
    static final int SECOND_WAIT = 5;
    static final int DELIVERING = 6;
    static final int DONE = 7;

    private static final int NEAR_NPC_PX = 80;
    private static final int NEAR_OWNER_PX = 80;
    private static final long WAIT_MS = 1800;

    private static final AgentScript SCRIPT = new AgentScript() {
        private final List<AgentScriptStep> steps = List.of(
                moveToCloto(FIRST_WALK),
                AgentScriptStep.of(ctx -> {
                    AgentBotPqRuntime.setKpqStageState(ctx.entry(), FIRST_WAIT);
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, AgentScriptContext::waitDone),
                AgentScriptStep.action(AgentKpqStage1::assignCouponTarget),
                AgentScriptStep.of(ctx -> {
                    AgentBotPqRuntime.setKpqStageState(ctx.entry(), GRINDING);
                    ctx.queueGrind();
                }, AgentKpqStage1::tickCouponGrinding, AgentKpqStage1::hasRequiredCoupons),
                moveToCloto(SECOND_WALK),
                AgentScriptStep.of(ctx -> {
                    AgentBotPqRuntime.setKpqStageState(ctx.entry(), SECOND_WAIT);
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, AgentScriptContext::waitDone),
                AgentScriptStep.action(AgentKpqStage1::exchangeCoupons),
                AgentScriptStep.of(AgentKpqStage1::queuePassDelivery, null, AgentScriptContext::tasksDone),
                AgentScriptStep.action(ctx -> {
                    AgentBotPqRuntime.queueSay(ctx.entry(), "Here's your pass!");
                    AgentBotPqRuntime.setKpqStageState(ctx.entry(), DONE);
                })
        );

        @Override
        public String id() {
            return "kpq-stage-1";
        }

        @Override
        public boolean applies(BotEntry entry, Character bot, Character owner) {
            if (bot.getMapId() != KPQ_STAGE1_MAP) {
                if (!AgentBotPqRuntime.kpqStageStateIs(entry, IDLE)) {
                    reset(entry);
                }
                return false;
            }
            return !AgentBotPqRuntime.kpqStageStateIs(entry, DONE);
        }

        @Override
        public List<AgentScriptStep> steps() {
            return steps;
        }
    };

    public static AgentScript script() {
        return SCRIPT;
    }

    public static boolean shouldSkipCouponLoot(AgentRuntimeEntry entry) {
        return AgentBotPqRuntime.kpqStageStateAtLeast(entry, SECOND_WALK);
    }

    public static boolean isNpcLocked(AgentRuntimeEntry entry) {
        return AgentBotPqRuntime.kpqStageStateIs(entry, FIRST_WAIT)
                || AgentBotPqRuntime.kpqStageStateIs(entry, SECOND_WAIT);
    }

    private static AgentScriptStep moveToCloto(int state) {
        return AgentScriptStep.of(ctx -> {
            AgentBotPqRuntime.setKpqStageState(ctx.entry(), state);
            queueMoveToCloto(ctx);
        }, AgentKpqStage1::tickMoveToCloto, ctx -> {
            Point npcPos = getNpcPos(ctx.bot());
            return npcPos != null && near(ctx.bot(), npcPos, NEAR_NPC_PX) && ctx.tasksDone();
        });
    }

    private static void tickMoveToCloto(AgentScriptContext ctx) {
        if (ctx.tasksDone()) {
            queueMoveToCloto(ctx);
        }
    }

    private static void queueMoveToCloto(AgentScriptContext ctx) {
        Point npcPos = getNpcPos(ctx.bot());
        if (npcPos != null && !near(ctx.bot(), npcPos, NEAR_NPC_PX)) {
            ctx.queueMoveTo(npcPos, false);
        }
    }

    private static void assignCouponTarget(AgentScriptContext ctx) {
        EventInstanceManager eim = ctx.bot().getEventInstance();
        int question = (eim != null) ? eim.gridCheck(ctx.bot()) : -1;
        if (question == -1) {
            question = ThreadLocalRandom.current().nextInt(1, ANSWERS.length);
            if (eim != null) eim.gridInsert(ctx.bot(), question);
        }
        int target = (question < ANSWERS.length) ? ANSWERS[question] : ANSWERS[1];
        AgentBotPqRuntime.setKpqCouponTarget(ctx.entry(), target);
        if (target >= 25) {
            AgentBotPqRuntime.queueSay(ctx.entry(), "I need " + target + ", smh");
        } else {
            AgentBotPqRuntime.queueSay(ctx.entry(), "I need " + target + ", Let's go!");
        }
    }

    private static void tickCouponGrinding(AgentScriptContext ctx) {
        int have = ctx.bot().getItemQuantity(ITEM_COUPON, false);
        int need = AgentBotPqRuntime.kpqCouponTarget(ctx.entry());

        if (have > need) {
            ctx.dropItem(InventoryType.ETC, ITEM_COUPON, (short) (have - need));
            have = need;
        }

        int milestone = (have / 5) * 5;
        if (milestone > AgentBotPqRuntime.kpqLastReportedCoupons(ctx.entry())) {
            AgentBotPqRuntime.setKpqLastReportedCoupons(ctx.entry(), milestone);
            AgentBotPqRuntime.queueSay(ctx.entry(), have + " / " + need);
        }
    }

    private static boolean hasRequiredCoupons(AgentScriptContext ctx) {
        int need = AgentBotPqRuntime.kpqCouponTarget(ctx.entry());
        if (need <= 0 || ctx.bot().getItemQuantity(ITEM_COUPON, false) < need) {
            return false;
        }
        AgentBotPqRuntime.queueSay(ctx.entry(), "Got " + need + "!");
        return true;
    }

    private static void exchangeCoupons(AgentScriptContext ctx) {
        int target = AgentBotPqRuntime.kpqCouponTarget(ctx.entry());
        if (ctx.bot().getItemQuantity(ITEM_COUPON, false) >= target) {
            InventoryManipulator.removeById(ctx.bot().getClient(), InventoryType.ETC, ITEM_COUPON, target, false, false);
            InventoryManipulator.addById(ctx.bot().getClient(), ITEM_PASS, (short) 1);
            EventInstanceManager eim = ctx.bot().getEventInstance();
            if (eim != null) eim.gridInsert(ctx.bot(), 0);
        }
        AgentBotPqRuntime.queueSay(ctx.entry(), "Got my pass! Bringing it to you.");
    }

    private static void queuePassDelivery(AgentScriptContext ctx) {
        AgentBotPqRuntime.setKpqStageState(ctx.entry(), DELIVERING);
        ctx.queueFollowUntilNearOwner(NEAR_OWNER_PX);
        ctx.queueDrop(InventoryType.ETC, ITEM_COUPON, (short) 0);
        ctx.queueDrop(InventoryType.ETC, ITEM_PASS, (short) 1);
    }

    private static void reset(BotEntry entry) {
        AgentBotPqRuntime.resetKpqStage1(entry, IDLE);
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
