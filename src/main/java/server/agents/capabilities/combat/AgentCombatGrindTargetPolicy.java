package server.agents.capabilities.combat;

import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;
import server.life.Monster;
import server.maps.Foothold;

public final class AgentCombatGrindTargetPolicy {
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
}
