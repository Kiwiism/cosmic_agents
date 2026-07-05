package server.agents.capabilities.supplies;

import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeHandle;

public record AgentPotionDonorPlan<E extends AgentRuntimeHandle>(E entry, int count) {
    public boolean qualifies() {
        return count > AgentRuntimeConfig.cfg.POT_LOW_WARN * 3;
    }

    public int donationQty() {
        return count / 3;
    }
}
