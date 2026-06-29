package server.agents.capabilities.combat;

import java.util.function.IntSupplier;
import server.maps.Foothold;

public final class AgentCombatGrindTargetPolicy {
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
}
