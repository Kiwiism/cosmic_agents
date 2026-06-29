package server.agents.integration;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotShopManager;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned runtime context for the temporary BotShopManager purchase flow.
 */
public record AgentBotShopPurchaseSequence(BotEntry entry,
                                           Character bot,
                                           Point npcPos,
                                           List<AgentBotShopPurchaseAction> actions,
                                           List<String> bought,
                                           BotShopManager.BuyReport firstShortfall) {
    public AgentBotShopPurchaseSequence withFirstShortfall(BotShopManager.BuyReport report) {
        if (firstShortfall == null && report != null && report.hasShortfall()) {
            return new AgentBotShopPurchaseSequence(entry, bot, npcPos, actions, bought, report);
        }
        return this;
    }
}
