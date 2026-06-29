package server.agents.capabilities.combat;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import server.life.Monster;
import server.maps.Foothold;

public final class AgentCombatGrindTargetPolicy {
    private static final long REGION_CROWD_BONUS_CAP = 3_000L;
    private static final long REGION_CROWD_BONUS_PER_EXTRA_MOB = 400L;

    private static final Comparator<AgentScoredGrindTarget> LEGACY_TARGET_ORDER = Comparator
            .comparingLong(AgentScoredGrindTarget::graphCost)
            .thenComparingLong(AgentScoredGrindTarget::localScore)
            .thenComparingDouble(AgentScoredGrindTarget::distanceSq);

    private AgentCombatGrindTargetPolicy() {
    }

    public static boolean isLocalCombatTarget(Foothold agentFoothold,
                                              Foothold targetFoothold,
                                              boolean graphAvailable,
                                              IntSupplier targetRegionId,
                                              int startRegionId) {
        if (agentFoothold != null && targetFoothold != null
                && targetFoothold.getId() == agentFoothold.getId()) {
            return true;
        }
        if (!graphAvailable) {
            return false;
        }

        int resolvedTargetRegionId = targetRegionId.getAsInt();
        return resolvedTargetRegionId >= 0 && resolvedTargetRegionId == startRegionId;
    }

    public static void sortByLegacyTargetOrder(List<AgentScoredGrindTarget> scoredTargets) {
        scoredTargets.sort(LEGACY_TARGET_ORDER);
    }

    public static Monster pickFromBestTargets(List<AgentScoredGrindTarget> scoredTargets) {
        if (scoredTargets.isEmpty()) {
            return null;
        }
        sortByLegacyTargetOrder(scoredTargets);
        return scoredTargets.get(0).monster();
    }

    public static List<AgentScoredGrindTarget> scoreLocalTargets(List<Monster> candidates,
                                                                 Point agentPosition,
                                                                 ToLongFunction<Monster> localScore,
                                                                 ToLongFunction<Monster> clusterBonus) {
        List<AgentScoredGrindTarget> scoredTargets = new ArrayList<>(candidates.size());
        for (Monster candidate : candidates) {
            long adjustedLocalScore = localScore.applyAsLong(candidate) - clusterBonus.applyAsLong(candidate);
            scoredTargets.add(new AgentScoredGrindTarget(candidate, adjustedLocalScore, adjustedLocalScore,
                    candidate.getPosition().distanceSq(agentPosition)));
        }
        return scoredTargets;
    }

    public static List<AgentScoredGrindTarget> scoreTargetRegions(List<Monster> candidates,
                                                                  Point agentPosition,
                                                                  ToIntFunction<Monster> targetRegionId,
                                                                  ToLongFunction<Monster> localScore,
                                                                  ToLongFunction<AgentGrindTargetGroup> pathCost,
                                                                  ToLongFunction<AgentGrindTargetGroup> occupancyPenalty,
                                                                  long unreachableGraphCost) {
        Map<Integer, AgentGrindTargetGroup> groupsByRegionId = new HashMap<>();
        for (Monster candidate : candidates) {
            int regionId = targetRegionId.applyAsInt(candidate);
            if (regionId < 0) {
                continue;
            }

            AgentGrindTargetGroup group = groupsByRegionId.computeIfAbsent(regionId, AgentGrindTargetGroup::new);
            group.add(candidate, localScore.applyAsLong(candidate),
                    candidate.getPosition().distanceSq(agentPosition));
        }

        List<AgentScoredGrindTarget> scoredTargets = new ArrayList<>(groupsByRegionId.size());
        for (AgentGrindTargetGroup group : groupsByRegionId.values()) {
            scoredTargets.add(toScoredTarget(group, pathCost.applyAsLong(group),
                    occupancyPenalty.applyAsLong(group), unreachableGraphCost));
        }
        return scoredTargets;
    }

    public static List<AgentScoredGrindTarget> scoreFollowLocalTargets(List<Monster> candidates,
                                                                       Point agentPosition,
                                                                       Predicate<Monster> canSelect,
                                                                       ToLongFunction<Monster> localScore,
                                                                       ToLongFunction<Monster> clusterBonus) {
        List<AgentScoredGrindTarget> scoredTargets = new ArrayList<>();
        for (Monster candidate : candidates) {
            if (!canSelect.test(candidate)) {
                continue;
            }
            long adjustedLocalScore = localScore.applyAsLong(candidate) - clusterBonus.applyAsLong(candidate);
            scoredTargets.add(new AgentScoredGrindTarget(candidate, adjustedLocalScore, adjustedLocalScore,
                    candidate.getPosition().distanceSq(agentPosition)));
        }
        return scoredTargets;
    }

    public static long regionCrowdBonus(int mobCount) {
        return Math.min(REGION_CROWD_BONUS_CAP,
                (long) Math.max(0, mobCount - 1) * REGION_CROWD_BONUS_PER_EXTRA_MOB);
    }

    public static long graphScore(long pathCost, long crowdBonus, long occupancyPenalty, long unreachableGraphCost) {
        if (pathCost >= unreachableGraphCost) {
            return unreachableGraphCost;
        }
        return Math.max(0L, pathCost - crowdBonus) + occupancyPenalty;
    }

    public static long graphPathCost(boolean hasValidRegions,
                                     boolean sameRegion,
                                     long sameRegionLocalCost,
                                     List<Long> edgeCosts,
                                     long unreachableGraphCost) {
        if (!hasValidRegions) {
            return unreachableGraphCost;
        }
        if (sameRegion) {
            return sameRegionLocalCost;
        }
        if (edgeCosts == null || edgeCosts.isEmpty()) {
            return unreachableGraphCost;
        }

        long cost = 0L;
        for (Long edgeCost : edgeCosts) {
            cost += edgeCost == null ? 0L : edgeCost;
        }
        return cost;
    }

    public static long occupancyPenalty(int occupiedCount, int penaltyPerOccupiedRegion, int penaltyCap) {
        long penalty = (long) Math.max(0, occupiedCount) * Math.max(0, penaltyPerOccupiedRegion);
        return Math.min(Math.max(0, penaltyCap), penalty);
    }

    public static boolean shouldInspectRegionOccupant(boolean sameEntry,
                                                      boolean grinding,
                                                      boolean sameMap,
                                                      boolean alive,
                                                      boolean hasPosition) {
        return !sameEntry
                && grinding
                && sameMap
                && alive
                && hasPosition;
    }

    public static boolean shouldCountRegionOccupant(int occupiedRegionId, int targetRegionId) {
        return targetRegionId >= 0
                && occupiedRegionId == targetRegionId;
    }

    public static AgentScoredGrindTarget toScoredTarget(AgentGrindTargetGroup group,
                                                        long pathCost,
                                                        long occupancyPenalty,
                                                        long unreachableGraphCost) {
        long graphScore = graphScore(pathCost, regionCrowdBonus(group.mobCount()),
                occupancyPenalty, unreachableGraphCost);
        long localScore = group.bestLocalScore() + occupancyPenalty;
        return new AgentScoredGrindTarget(group.bestMonster(), graphScore, localScore,
                group.bestDistanceSq());
    }
}
