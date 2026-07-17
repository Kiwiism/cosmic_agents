package server.agents.plans.amherst;

import server.agents.capabilities.objective.ForceCompleteQuestObjectiveCapability;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ThreadLocalRandom;

final class MapleIslandForcedQuestBehavior {
    private static final long MIN_CASH_SHOP_VISIT_MS = 2_500L;
    private static final long MAX_CASH_SHOP_VISIT_MS = 5_000L;
    private static final long SAFETY_RESTORE_GRACE_MS = 2_000L;
    private static final long RETURN_LANDING_SETTLE_MS = 3_000L;

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
