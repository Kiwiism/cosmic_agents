package server.agents.capabilities.supplies;

import server.agents.runtime.AgentRuntimeHandle;

public record AgentAmmoDonorPlan<E extends AgentRuntimeHandle>(
        E entry,
        int matchingAmmoCount,
        boolean donorNeedsSameAmmo,
        int donationQty) {
}
