package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.objective.AgentObjectiveVariationRuntime;
import server.agents.capabilities.objective.AgentPlanCompletionMode;
import server.agents.capabilities.objective.AgentPlanCompletionPolicy;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.OptionalInt;

public final class MapleIslandPlanCompletionPolicy implements AgentPlanCompletionPolicy {
    private static final String FACE_HOLE_VARIATION_KEY = "southperry-face-hole-idle";
    private static final int FACE_HOLE_CHANCE_DENOMINATOR = 2;
    public static final MapleIslandPlanCompletionPolicy INSTANCE = new MapleIslandPlanCompletionPolicy();

    private MapleIslandPlanCompletionPolicy() {
    }

    @Override
    public AgentPlanCompletionMode selectMode(AgentRuntimeEntry entry, int mapId) {
        return mapId == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                ? MapleIslandObjectiveRandomnessRuntime.selectPostPlanBehavior(entry, mapId)
                : AgentPlanCompletionMode.SIT;
    }

    @Override
    public boolean startWander(AgentRuntimeEntry entry, Character agent) {
        return AgentSouthperryPostPlanService.startWander(entry, agent);
    }

    @Override
    public String locationName(AgentRuntimeEntry entry, int mapId) {
        return mapId == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                ? "Southperry" : AgentPlanCompletionPolicy.super.locationName(entry, mapId);
    }

    @Override
    public OptionalInt selectRestSpotIndex(
            AgentRuntimeEntry entry, int mapId, int candidateCount) {
        return MapleIslandObjectiveRandomnessRuntime.selectRestSpotIndex(
                entry, mapId, candidateCount);
    }

    @Override
    public OptionalInt selectFacingDirection(AgentRuntimeEntry entry, int mapId) {
        return MapleIslandObjectiveRandomnessRuntime.selectRestFacingDirection(entry, mapId);
    }

    MapleIslandRelaxerSpotCatalog.Pool selectRestSpotPool(
            AgentRuntimeEntry entry,
            int mapId,
            AgentPlanCompletionMode mode,
            MapleIslandRelaxerSpotCatalog.Pool fallback) {
        if (mapId == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                && mode == AgentPlanCompletionMode.IDLE
                && AgentObjectiveVariationRuntime.selectPlanVariant(
                entry, FACE_HOLE_VARIATION_KEY, FACE_HOLE_CHANCE_DENOMINATOR).orElse(1) == 0) {
            return MapleIslandRelaxerSpotCatalog.Pool.SOUTHPERRY_FACE_HOLES;
        }
        return fallback;
    }
}
