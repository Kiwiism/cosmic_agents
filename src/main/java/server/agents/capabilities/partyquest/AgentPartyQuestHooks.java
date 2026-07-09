package server.agents.capabilities.partyquest;

import client.Character;
import server.agents.capabilities.partyquest.kpq.AgentKpqStage1;
import server.agents.capabilities.partyquest.kpq.AgentKpqStage5;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.plans.AgentScript;
import server.agents.plans.AgentScriptRunner;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Single call-site for all per-map party-quest bot automation.
 * Agent common tick calls {@link #tick} once per Agent tick; each PQ class
 * handles its own map range.
 */
public final class AgentPartyQuestHooks {
    private static final List<AgentScript> SCRIPTS = List.of(AgentKpqStage1.script());

    private AgentPartyQuestHooks() {}

    public static void tick(AgentRuntimeEntry entry, Character bot, Character owner, InventoryGateway inventory) {
        AgentScriptRunner.tick(entry, bot, owner, SCRIPTS);
        AgentKpqStage5.tick(entry, bot, inventory);
    }

    /**
     * Returns true when the bot should stand idle at an NPC and skip normal AI.
     */
    public static boolean isNpcLocked(AgentRuntimeEntry entry) {
        return AgentKpqStage1.isNpcLocked(entry);
    }

    /** Returns true if the bot is in a PQ map that requires grind mode (KPQ stage 1). */
    public static boolean requiresGrind(AgentRuntimeEntry entry, Character bot) {
        return bot.getMapId() == AgentKpqStage1.KPQ_STAGE1_MAP
                && AgentPqRuntime.kpqStageState(entry) == AgentKpqStage1.GRINDING;
    }

    /** True once the bot no longer needs coupons — suppress coupon loot. */
    public static boolean shouldSkipCouponLoot(AgentRuntimeEntry entry) {
        return AgentKpqStage1.shouldSkipCouponLoot(entry);
    }

    /** Returns true if the bot is in a PQ map that should default to follow mode (KPQ stages 2-5). */
    public static boolean requiresFollow(AgentRuntimeEntry entry, Character bot) {
        int mapId = bot.getMapId();
        return mapId >= 103000801 && mapId <= 103000805;
    }
}
