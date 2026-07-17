package server.agents.capabilities.quest;

import server.agents.plans.amherst.AmherstQuestCatalog;

public final class AmherstTestResetPlanner {
    public AmherstTestResetPlan plan(AmherstTestResetRequest request) {
        return switch (request.mode()) {
            case CLEAN_LV1_START -> fullReset(request.mode(), AmherstQuestCatalog.START_MAP_ID, false);
            case AMHERST_MVP_CLEAN -> fullReset(request.mode(), AmherstQuestCatalog.START_MAP_ID, false);
            case AMHERST_READY -> fullReset(request.mode(), AmherstQuestCatalog.FINAL_MAP_ID, true);
            case SOUTHPERRY_MVP_START -> new AmherstTestResetPlan(
                    request.mode(),
                    MapleIslandSouthperryBaseline.snapshot().character().mapId(),
                    true,
                    true,
                    0,
                    false,
                    true);
            case QUEST_SCENARIO -> new AmherstTestResetPlan(
                    request.mode(),
                    mapFor(AmherstQuestCatalog.find(request.questId()).orElseThrow(
                            () -> new IllegalArgumentException("quest scenario is outside Amherst scope")).segment()),
                    false,
                    false,
                    request.questId(),
                    false,
                    false);
        };
    }

    private static AmherstTestResetPlan fullReset(AmherstTestResetMode mode,
                                                   int mapId,
                                                   boolean seedPrerequisites) {
        return new AmherstTestResetPlan(mode, mapId, true, true, 0, seedPrerequisites, false);
    }

    private static int mapFor(AmherstQuestSegment segment) {
        return switch (segment) {
            case MUSHROOM_TOWN -> 10000;
            case SNAIL_GARDEN -> 20000;
            case SNAIL_FIELD -> 30000;
            case TUTORIAL_HUNTING_GROUND -> 40000;
            case DANGEROUS_FOREST -> 50000;
            case AMHERST -> 1000000;
            case TRAINING_CENTER -> 1010000;
            case SOUTHPERRY -> 2000000;
        };
    }
}
