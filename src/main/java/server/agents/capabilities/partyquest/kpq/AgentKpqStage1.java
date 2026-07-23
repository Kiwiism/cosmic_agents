package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.plans.AgentScript;
import server.agents.plans.AgentScriptContext;
import server.agents.plans.AgentScriptStep;
import server.agents.runtime.AgentRuntimeEntry;
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
    private static final int NPC_CLOTO_ID = 9_020_001;
    private static final int ITEM_COUPON_ID = 4_001_007;
    private static final int ITEM_PASS_ID = 4_001_008;

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

    private static final int NEAR_NPC_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqStage1.NEAR_NPC_PX");
    private static final int NEAR_OWNER_PX = config.AgentTuning.intValue("server.agents.capabilities.partyquest.kpq.AgentKpqStage1.NEAR_OWNER_PX");
    private static final long WAIT_MS = config.AgentTuning.longValue("server.agents.capabilities.partyquest.kpq.AgentKpqStage1.WAIT_MS");

    private static final AgentScript SCRIPT = new AgentScript() {
        private final List<AgentScriptStep> steps = List.of(
                moveToCloto(FIRST_WALK),
                AgentScriptStep.of(ctx -> {
                    AgentPqRuntime.setKpqStageState(ctx.entry(), FIRST_WAIT);
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, AgentScriptContext::waitDone),
                AgentScriptStep.action(AgentKpqStage1::assignCouponTarget),
                AgentScriptStep.of(ctx -> {
                    AgentPqRuntime.setKpqStageState(ctx.entry(), GRINDING);
                    ctx.queueGrind();
                }, AgentKpqStage1::tickCouponGrinding, AgentKpqStage1::hasRequiredCoupons),
                moveToCloto(SECOND_WALK),
                AgentScriptStep.of(ctx -> {
                    AgentPqRuntime.setKpqStageState(ctx.entry(), SECOND_WAIT);
                    ctx.queueStop();
                    ctx.waitMs(WAIT_MS);
                }, null, AgentScriptContext::waitDone),
                AgentScriptStep.action(AgentKpqStage1::exchangeCoupons),
                AgentScriptStep.of(AgentKpqStage1::queuePassDelivery, null, AgentScriptContext::tasksDone),
                AgentScriptStep.action(ctx -> {
                    AgentPqRuntime.queueSay(ctx.entry(), "Here's your pass!");
                    AgentPqRuntime.setKpqStageState(ctx.entry(), DONE);
                })
        );

        @Override
        public String id() {
            return "kpq-stage-1";
        }

        @Override
        public boolean applies(AgentRuntimeEntry entry, Character bot, Character owner) {
            if (bot.getMapId() != KPQ_STAGE1_MAP) {
                if (!AgentPqRuntime.kpqStageStateIs(entry, IDLE)) {
                    reset(entry);
                }
                return false;
            }
            return !AgentPqRuntime.kpqStageStateIs(entry, DONE);
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
        return AgentPqRuntime.kpqStageStateAtLeast(entry, SECOND_WALK);
    }

    public static boolean isNpcLocked(AgentRuntimeEntry entry) {
        return AgentPqRuntime.kpqStageStateIs(entry, FIRST_WAIT)
                || AgentPqRuntime.kpqStageStateIs(entry, SECOND_WAIT);
    }

    private static AgentScriptStep moveToCloto(int state) {
        return AgentScriptStep.of(ctx -> {
            AgentPqRuntime.setKpqStageState(ctx.entry(), state);
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
        AgentPqRuntime.setKpqCouponTarget(ctx.entry(), target);
        if (target >= 25) {
            AgentPqRuntime.queueSay(ctx.entry(), "I need " + target + ", smh");
        } else {
            AgentPqRuntime.queueSay(ctx.entry(), "I need " + target + ", Let's go!");
        }
    }

    private static void tickCouponGrinding(AgentScriptContext ctx) {
        int have = ctx.bot().getItemQuantity(ITEM_COUPON_ID, false);
        int need = AgentPqRuntime.kpqCouponTarget(ctx.entry());

        if (have > need) {
            ctx.dropItem(InventoryType.ETC, ITEM_COUPON_ID, (short) (have - need));
            have = need;
        }

        int milestone = (have / 5) * 5;
        if (milestone > AgentPqRuntime.kpqLastReportedCoupons(ctx.entry())) {
            AgentPqRuntime.setKpqLastReportedCoupons(ctx.entry(), milestone);
            AgentPqRuntime.queueSay(ctx.entry(), have + " / " + need);
        }
    }

    private static boolean hasRequiredCoupons(AgentScriptContext ctx) {
        int need = AgentPqRuntime.kpqCouponTarget(ctx.entry());
        if (need <= 0 || ctx.bot().getItemQuantity(ITEM_COUPON_ID, false) < need) {
            return false;
        }
        AgentPqRuntime.queueSay(ctx.entry(), "Got " + need + "!");
        return true;
    }

    private static void exchangeCoupons(AgentScriptContext ctx) {
        int target = AgentPqRuntime.kpqCouponTarget(ctx.entry());
        exchangeCouponsForPass(ctx.bot(), target, AgentInventoryGatewayRuntime.inventory());
        AgentPqRuntime.queueSay(ctx.entry(), "Got my pass! Bringing it to you.");
    }

    static boolean exchangeCouponsForPass(Character bot, int target, InventoryGateway inventory) {
        if (bot.getItemQuantity(ITEM_COUPON_ID, false) < target) {
            return false;
        }
        inventory.removeById(bot, InventoryType.ETC, ITEM_COUPON_ID, target, false, false);
        inventory.addItem(bot, ITEM_PASS_ID, (short) 1);
        EventInstanceManager eim = bot.getEventInstance();
        if (eim != null) eim.gridInsert(bot, 0);
        return true;
    }

    private static void queuePassDelivery(AgentScriptContext ctx) {
        AgentPqRuntime.setKpqStageState(ctx.entry(), DELIVERING);
        ctx.queueFollowUntilNearOwner(NEAR_OWNER_PX);
        ctx.queueDrop(InventoryType.ETC, ITEM_COUPON_ID, (short) 0);
        ctx.queueDrop(InventoryType.ETC, ITEM_PASS_ID, (short) 1);
    }

    private static void reset(AgentRuntimeEntry entry) {
        AgentPqRuntime.resetKpqStage1(entry, IDLE);
    }

    private static Point getNpcPos(Character bot) {
        NPC npc = bot.getMap().getNPCById(NPC_CLOTO_ID);
        return (npc != null) ? npc.getPosition() : null;
    }

    private static boolean near(Character bot, Point target, int dist) {
        Point p = bot.getPosition();
        return Math.abs(p.x - target.x) <= dist && Math.abs(p.y - target.y) <= dist;
    }
}
