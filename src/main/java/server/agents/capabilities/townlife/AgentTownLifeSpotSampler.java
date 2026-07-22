package server.agents.capabilities.townlife;

import client.Character;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.reservation.CharacterSpace;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AgentTownLifeSpotSampler {
    private static final int MAX_SPOTS_PER_REGION = 3;
    private static final int EDGE_INSET_PX = 30;

    private AgentTownLifeSpotSampler() {
    }

    static List<CharacterSpace> reachableSpaces(AgentRuntimeEntry entry,
                                                Character agent,
                                                List<Point> activityAnchors,
                                                long variationSeed) {
        MapleMap map = agent.getMap();
        if (map == null) {
            return List.of();
        }
        AgentMovementProfile profile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(entry, map, profile);
            return List.of();
        }
        int originRegionId = graph.findRegionId(map, agent.getPosition());
        int originComponent = originRegionId < 0 ? -1 : graph.connectedComponentId(originRegionId);
        int mainGroundY = graph.regions.stream()
                .filter(region -> !region.isRopeRegion)
                .mapToInt(region -> region.maxY)
                .max()
                .orElse(agent.getPosition().y);
        List<WeightedSpace> weighted = new ArrayList<>();
        int spotNumber = 1;
        for (AgentNavigationGraph.Region region : graph.regions.stream()
                .sorted(Comparator.comparingInt(region -> region.id)).toList()) {
            if (region.isRopeRegion || region.width() < 48
                    || (originComponent >= 0 && graph.connectedComponentId(region.id) != originComponent)) {
                continue;
            }
            int count = Math.min(MAX_SPOTS_PER_REGION, Math.max(1, region.width() / 180));
            int inset = Math.min(EDGE_INSET_PX, Math.max(8, region.width() / 8));
            for (int slot = 0; slot < count; slot++) {
                int usableWidth = Math.max(0, region.width() - inset * 2);
                int x = region.minX + inset + (slot + 1) * usableWidth / (count + 1);
                Point point = region.pointAt(x);
                int anchorBonus = activityAnchors.stream()
                        .mapToInt(anchor -> anchor.distanceSq(point) <= 220L * 220L ? 180 : 0)
                        .sum();
                int heightPenalty = Math.abs(point.y - mainGroundY) / 4;
                int weight = Math.max(10, Math.min(500, region.width() + anchorBonus - heightPenalty));
                CharacterSpace space = new CharacterSpace(
                        "town-nav-" + map.getId(), spotNumber++, map.getId(),
                        Math.max(0, region.id), slot,
                        point.x, point.y);
                weighted.add(new WeightedSpace(space, weight,
                        orderingScore(variationSeed, space.spotNumber(), weight)));
            }
        }
        weighted.sort(Comparator.comparingDouble(WeightedSpace::score));
        return weighted.stream().map(WeightedSpace::space).toList();
    }

    private static double orderingScore(long seed, int spotNumber, int weight) {
        long mixed = seed + spotNumber * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        long positive = (mixed ^ mixed >>> 31) & Long.MAX_VALUE;
        return (positive / (double) Long.MAX_VALUE) / weight;
    }

    private record WeightedSpace(CharacterSpace space, int weight, double score) {
    }
}
