package server.agents.capabilities.shop;

import client.Character;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned runtime context for the temporary AgentShopService purchase flow.
 */
public record AgentShopPurchaseSequence(BotEntry entry,
                                        Character bot,
                                        Point npcPos,
                                        List<AgentShopPurchaseAction> actions,
                                        List<String> bought,
                                        AgentShopBuyReport firstShortfall) {
    public AgentShopPurchaseSequence withFirstShortfall(AgentShopBuyReport report) {
        if (firstShortfall == null && report != null && report.hasShortfall()) {
            return new AgentShopPurchaseSequence(entry, bot, npcPos, actions, bought, report);
        }
        return this;
    }
}
