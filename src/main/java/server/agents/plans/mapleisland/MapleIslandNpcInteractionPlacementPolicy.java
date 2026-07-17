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
    private static final int SNAIL_FIELD_OF_FLOWERS_MAP_ID = 30001;
    private static final int NINA_NPC_ID = 2001;
    private static final int SNAIL_GARDEN_MAP_ID = 20000;
    private static final int ROGER_NPC_ID = 2000;
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
        int range = MapleIslandNpcInteractionRadiusCatalog.requiredInteractionRangePx(
                mapId, npcId, defaultRangePx);
        var offset = MapleIslandNpcInteractionRadiusCatalog.centerOffset(mapId, npcId);
        boolean dynamicSpread = settings.enabled() && settings.npcAnchorVariationEnabled()
                && !usesCuratedReachableSlots(mapId, npcId);
        return new server.agents.capabilities.objective.AgentNpcInteractionPlacementData(
                range,
                MapleIslandNpcInteractionAnchorCatalog.anchors(mapId, npcId),
                MapleIslandNpcInteractionAnchorCatalog.legacyAnchorsFor(mapId, npcId),
                TRAFFIC_BIAS_X.get(new NpcPlacement(mapId, npcId)),
                dynamicSpread,
                settings.enabled(),
                new Point(offset.x(), offset.y()),
                MapleIslandNpcInteractionRadiusCatalog.radiusPx(mapId, npcId));
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
                ? MapleIslandNpcInteractionRadiusCatalog.requiredInteractionRangePx(
                mapId, npcId, defaultRangePx) : defaultRangePx;
        var offset = MapleIslandNpcInteractionRadiusCatalog.centerOffset(mapId, npcId);
        Point placementCenter = settings.enabled() && settings.npcAnchorVariationEnabled()
                ? new Point(npcPosition.x + offset.x(), npcPosition.y + offset.y())
                : new Point(npcPosition);
        int placementRadiusPx = settings.enabled() && settings.npcAnchorVariationEnabled()
                ? MapleIslandNpcInteractionRadiusCatalog.radiusPx(mapId, npcId) : defaultRangePx;
        List<Point> curated = MapleIslandNpcInteractionAnchorCatalog.anchors(mapId, npcId).stream()
                .filter(candidate -> candidate.distanceSq(placementCenter)
                        <= (long) placementRadiusPx * placementRadiusPx)
                .toList();
        List<Point> spread = usesCuratedReachableSlots(mapId, npcId) ? List.of()
                : AgentNpcInteractionSpreadService.candidates(
                        agent, currentPosition, placementCenter, placementRadiusPx);
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

    private static boolean usesCuratedReachableSlots(int mapId, int npcId) {
        // Roger's y=65 foothold is enclosed by walls and has no ladder. Nina's
        // ladder is reachable, but represents one traffic lane rather than
        // three independent vertical waiting slots.
        return mapId == SNAIL_GARDEN_MAP_ID && npcId == ROGER_NPC_ID
                || mapId == SNAIL_FIELD_OF_FLOWERS_MAP_ID && npcId == NINA_NPC_ID;
    }

    private record NpcPlacement(int mapId, int npcId) {
    }
}
