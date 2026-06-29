package server.agents.integration;

import server.bots.BotEntry;

public record AgentBotAmmoDonorPlan(BotEntry entry,
                                    int matchingAmmoCount,
                                    boolean donorNeedsSameAmmo,
                                    int donationQty) {
}
