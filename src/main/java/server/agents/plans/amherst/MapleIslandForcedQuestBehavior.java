package server.agents.plans.amherst;

import server.agents.capabilities.objective.ForceCompleteQuestObjectiveCapability;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ThreadLocalRandom;

final class MapleIslandForcedQuestBehavior {
    private static final long MIN_CASH_SHOP_VISIT_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandForcedQuestBehavior.MIN_CASH_SHOP_VISIT_MS");
    private static final long MAX_CASH_SHOP_VISIT_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandForcedQuestBehavior.MAX_CASH_SHOP_VISIT_MS");
    private static final long SAFETY_RESTORE_GRACE_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandForcedQuestBehavior.SAFETY_RESTORE_GRACE_MS");
    private static final long RETURN_LANDING_SETTLE_MS = config.AgentTuning.longValue("server.agents.plans.amherst.MapleIslandForcedQuestBehavior.RETURN_LANDING_SETTLE_MS");

    private MapleIslandForcedQuestBehavior() {
    }

    static ForceCompleteQuestObjectiveCapability.FieldAbsence fieldAbsence(
            AgentRuntimeEntry entry, int questId) {
        if (questId != MapleIslandSouthperryQuestCatalog.YOONA_SHOPPING_GUIDE_QUEST_ID) {
            return null;
        }
        long durationMs = MapleIslandObjectiveRandomnessRuntime.sampleCashShopVisitDelayMs(
                        entry, MIN_CASH_SHOP_VISIT_MS, MAX_CASH_SHOP_VISIT_MS)
                .orElseGet(() -> ThreadLocalRandom.current().nextLong(
                        MIN_CASH_SHOP_VISIT_MS, MAX_CASH_SHOP_VISIT_MS + 1L));
        return new ForceCompleteQuestObjectiveCapability.FieldAbsence(
                durationMs, SAFETY_RESTORE_GRACE_MS,
                RETURN_LANDING_SETTLE_MS, "Cash Shop");
    }
}
