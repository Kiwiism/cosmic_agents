package server.agents.plans.mapleisland;

import client.Character;
import server.agents.capabilities.objective.AgentNpcInteractionPlacementPolicy;
import server.agents.capabilities.objective.AgentNpcInteractionSpreadService;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;
import java.util.Map;

public final class MapleIslandNpcInteractionPlacementPolicy
        implements AgentNpcInteractionPlacementPolicy {
    public static final MapleIslandNpcInteractionPlacementPolicy INSTANCE =
            new MapleIslandNpcInteractionPlacementPolicy();
    private static final int DANGEROUS_FOREST_MAP_ID = 50000;
    private static final int SAM_NPC_ID = 2005;
    private static final int SAM_COHORT_INTERACTION_RANGE_PX = 500;
    private static final Map<NpcPlacement, Integer> TRAFFIC_BIAS_X = Map.ofEntries(
            Map.entry(new NpcPlacement(10000, 2100), -120),
            Map.entry(new NpcPlacement(20000, 2000), 100),
            Map.entry(new NpcPlacement(30000, 2102), 100),
            Map.entry(new NpcPlacement(30001, 2001), -100),
            Map.entry(new NpcPlacement(50000, 2005), -160),
            Map.entry(new NpcPlacement(1010000, 20100), -100),
            Map.entry(new NpcPlacement(1010000, 12100), 0),
            Map.entry(new NpcPlacement(1010000, 20001), 100));

    private MapleIslandNpcInteractionPlacementPolicy() {
    }

    public static server.agents.capabilities.objective.AgentNpcInteractionPlacementData data(
            AgentRuntimeEntry entry, int mapId, int npcId, int defaultRangePx) {
        MapleIslandObjectiveRandomnessSettings settings = MapleIslandObjectiveRandomnessRuntime.settings(entry);
        if (!settings.enabled() || !settings.npcAnchorVariationEnabled()) {
            return server.agents.capabilities.objective.AgentNpcInteractionPlacementData.direct(defaultRangePx);
        }
        int range = settings.enabled() && settings.npcAnchorVariationEnabled()
                && mapId == DANGEROUS_FOREST_MAP_ID && npcId == SAM_NPC_ID
                ? Math.max(defaultRangePx, SAM_COHORT_INTERACTION_RANGE_PX) : defaultRangePx;
        return new server.agents.capabilities.objective.AgentNpcInteractionPlacementData(
                range,
                MapleIslandNpcInteractionAnchorCatalog.anchors(mapId, npcId),
                MapleIslandNpcInteractionAnchorCatalog.legacyAnchorsFor(mapId, npcId),
                TRAFFIC_BIAS_X.get(new NpcPlacement(mapId, npcId)),
                settings.enabled() && settings.npcAnchorVariationEnabled(),
                settings.enabled());
    }

    @Override
    public Placement select(AgentRuntimeEntry entry,
                            Character agent,
                            int mapId,
                            int npcId,
                            Point currentPosition,
                            Point npcPosition,
                            int defaultRangePx) {
        MapleIslandObjectiveRandomnessSettings settings =
                MapleIslandObjectiveRandomnessRuntime.settings(entry);
        int interactionRangePx = settings.enabled() && settings.npcAnchorVariationEnabled()
                && mapId == DANGEROUS_FOREST_MAP_ID && npcId == SAM_NPC_ID
                ? Math.max(defaultRangePx, SAM_COHORT_INTERACTION_RANGE_PX) : defaultRangePx;
        List<Point> curated = MapleIslandNpcInteractionAnchorCatalog.anchors(mapId, npcId).stream()
                .filter(candidate -> candidate.distanceSq(npcPosition)
                        <= (long) interactionRangePx * interactionRangePx)
                .toList();
        List<Point> spread = AgentNpcInteractionSpreadService.candidates(
                agent, currentPosition, npcPosition, interactionRangePx);
        List<Point> candidates = spread.size() >= 2
                ? weightedPool(spread, currentPosition, mapId, npcId, npcPosition)
                : curated;
        var selected = MapleIslandObjectiveRandomnessRuntime.selectNpcAnchorIndex(
                entry, mapId, npcId, candidates.size());
        Point anchor = selected.isPresent()
                ? candidates.get(selected.getAsInt())
                : MapleIslandNpcInteractionAnchorCatalog.nearestLegacy(mapId, npcId, currentPosition);
        return new Placement(interactionRangePx, anchor,
                AgentNpcInteractionSpreadService.isClimbableAnchor(agent, anchor));
    }

    @Override
    public boolean distinguishesInteractionStages(AgentRuntimeEntry entry) {
        return MapleIslandObjectiveRandomnessRuntime.settings(entry).enabled();
    }

    static List<Point> weightedPool(List<Point> candidates,
                                    Point arrivalPosition,
                                    int mapId,
                                    int npcId,
                                    Point npcPosition) {
        Integer biasX = TRAFFIC_BIAS_X.get(new NpcPlacement(mapId, npcId));
        Point focus = biasX == null || npcPosition == null
                ? arrivalPosition : new Point(npcPosition.x + biasX, npcPosition.y);
        return AgentNpcInteractionSpreadService.selectionPool(candidates, focus);
    }

    private record NpcPlacement(int mapId, int npcId) {
    }
}
