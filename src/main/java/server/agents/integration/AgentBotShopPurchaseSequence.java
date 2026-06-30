package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned runtime context for the temporary AgentShopService purchase flow.
 */
public record AgentBotShopPurchaseSequence(BotEntry entry,
                                           Character bot,
                                           Point npcPos,
                                           List<AgentBotShopPurchaseAction> actions,
                                           List<String> bought,
                                           AgentBotShopBuyReport firstShortfall) {
    public AgentBotShopPurchaseSequence withFirstShortfall(AgentBotShopBuyReport report) {
        if (firstShortfall == null && report != null && report.hasShortfall()) {
            return new AgentBotShopPurchaseSequence(entry, bot, npcPos, actions, bought, report);
        }
        return this;
    }
}
