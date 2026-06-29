package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotManager;

public record AgentBotPotionDonorPlan(BotEntry entry, int count) {
    public boolean qualifies() {
        return count > BotManager.cfg.POT_LOW_WARN * 3;
    }

    public int donationQty() {
        return count / 3;
    }
}
