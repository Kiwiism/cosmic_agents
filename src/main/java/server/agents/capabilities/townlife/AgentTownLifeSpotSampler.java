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
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.townlife.AgentTownLifeSpotSampler.";
    private static final int ANCHOR_RADIUS_PX = tuningInt("ANCHOR_RADIUS_PX");
    private static final int ANCHOR_BONUS = tuningInt("ANCHOR_BONUS");
    private static final int BASE_WEIGHT = tuningInt("BASE_WEIGHT");
    private static final int AUTHORED_BASE_WEIGHT = tuningInt("AUTHORED_BASE_WEIGHT");
    private static final int MIN_WEIGHT = tuningInt("MIN_WEIGHT");
    private static final int MAX_WEIGHT = tuningInt("MAX_WEIGHT");
    private static final int DISTRICT_PREFERENCE_BONUS =
            tuningInt("DISTRICT_PREFERENCE_BONUS");
    private static final int PLATFORM_PREFERENCE_BONUS =
            tuningInt("PLATFORM_PREFERENCE_BONUS");

    private AgentTownLifeSpotSampler() {
    }

    static List<CharacterSpace> reachableSpaces(AgentRuntimeEntry entry,
                                                Character agent,
                                                AgentTownLifeState state,
                                                List<Point> activityAnchors,
                                                long variationSeed) {
        MapleMap map = agent.getMap();
        if (map == null) {
            return List.of();
        }
        AgentMovementProfile movementProfile = AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, movementProfile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(entry, map, movementProfile);
            return List.of();
        }
        int originRegionId = graph.findRegionId(map, agent.getPosition());
        int originComponent = originRegionId < 0 ? -1 : graph.connectedComponentId(originRegionId);
        AgentTownLifeProfile townProfile = AgentTownLifeProfileRepository.defaultRepository()
                .require(map.getId());
        List<WeightedSpace> weighted = new ArrayList<>();
        for (AgentTownLifePlatformCatalog.PlatformSpot spot :
                AgentTownLifePlatformCatalog.reachable(graph, townProfile, originComponent)) {
            CharacterSpace space = spot.space();
            if (!townProfile.allowsOccupancy(space.position())) {
                continue;
            }
            int anchorBonus = activityAnchors.stream()
                    .mapToInt(anchor -> anchor.distanceSq(space.position())
                            <= (long) ANCHOR_RADIUS_PX * ANCHOR_RADIUS_PX ? ANCHOR_BONUS : 0)
                    .sum();
            int preferenceBonus = preferenceBonus(state, spot.district(), spot.platformKind());
            int weight = Math.max(
                    MIN_WEIGHT,
                    Math.min(MAX_WEIGHT, BASE_WEIGHT + anchorBonus + preferenceBonus));
            weighted.add(new WeightedSpace(space, weight,
                    preferenceRank(state, spot.district(), spot.platformKind()),
                    orderingScore(variationSeed, space.spotNumber(), weight)));
        }
        weighted.sort(Comparator.comparingInt(WeightedSpace::preferenceRank)
                .thenComparingDouble(WeightedSpace::score));
        return weighted.stream().map(WeightedSpace::space).toList();
    }

    static List<CharacterSpace> orderAuthoredSpaces(Character agent,
                                                     AgentTownLifeState state,
                                                     List<CharacterSpace> spaces,
                                                     long variationSeed) {
        if (agent == null || state == null || spaces == null || spaces.size() < 2) {
            return spaces == null ? List.of() : spaces;
        }
        AgentTownLifeMapExtension extension =
                AgentTownLifeMapExtensionRepository.forMap(agent.getMapId());
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(agent.getMapId());
        return spaces.stream()
                .filter(space -> profile.allowsOccupancy(space.position()))
                .map(space -> {
                    AgentTownLifeState.District district = extension.classify(space.position());
                    int weight = AUTHORED_BASE_WEIGHT + preferenceBonus(
                            state, district, AgentTownLifeState.PlatformKind.ANY);
                    return new WeightedSpace(space, weight,
                            preferenceRank(state, district, AgentTownLifeState.PlatformKind.ANY),
                            orderingScore(variationSeed, space.spotNumber(), weight));
                })
                .sorted(Comparator.comparingInt(WeightedSpace::preferenceRank)
                        .thenComparingDouble(WeightedSpace::score))
                .map(WeightedSpace::space)
                .toList();
    }

    private static int preferenceBonus(AgentTownLifeState state,
                                       AgentTownLifeState.District district,
                                       AgentTownLifeState.PlatformKind platform) {
        int bonus = 0;
        AgentTownLifeState.District preferredDistrict = state.preferredDistrict();
        if (preferredDistrict != AgentTownLifeState.District.ANY
                && preferredDistrict == district) {
            bonus += DISTRICT_PREFERENCE_BONUS;
        }
        AgentTownLifeState.PlatformKind preferredPlatform = state.platformPreference();
        if (preferredPlatform != AgentTownLifeState.PlatformKind.ANY
                && preferredPlatform == platform) {
            bonus += PLATFORM_PREFERENCE_BONUS;
        }
        return bonus;
    }

    private static int preferenceRank(AgentTownLifeState state,
                                      AgentTownLifeState.District district,
                                      AgentTownLifeState.PlatformKind platform) {
        boolean districtMatch = state.preferredDistrict() == AgentTownLifeState.District.ANY
                || state.preferredDistrict() == district;
        boolean platformMatch = state.platformPreference() == AgentTownLifeState.PlatformKind.ANY
                || state.platformPreference() == platform
                || platform == AgentTownLifeState.PlatformKind.ANY;
        if (districtMatch && platformMatch) {
            return 0;
        }
        return districtMatch || platformMatch ? 1 : 2;
    }

    private static double orderingScore(long seed, int spotNumber, int weight) {
        long mixed = seed + spotNumber * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        long positive = (mixed ^ mixed >>> 31) & Long.MAX_VALUE;
        return (positive / (double) Long.MAX_VALUE) / weight;
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private record WeightedSpace(CharacterSpace space,
                                 int weight,
                                 int preferenceRank,
                                 double score) {
    }
}
