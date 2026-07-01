package server.agents.integration;

import server.agents.runtime.AgentRuntimeConfig;
import server.bots.BotEntry;

public record AgentBotPotionDonorPlan(BotEntry entry, int count) {
    public boolean qualifies() {
        return count > AgentRuntimeConfig.cfg.POT_LOW_WARN * 3;
    }

    public int donationQty() {
        return count / 3;
    }
}
