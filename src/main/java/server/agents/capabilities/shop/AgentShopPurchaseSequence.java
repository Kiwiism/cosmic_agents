package server.agents.capabilities.shop;

import client.Character;
import server.agents.runtime.AgentRuntimeHandle;

import java.awt.Point;
import java.util.List;

/**
 * Agent-owned runtime context for the temporary AgentShopService purchase flow.
 */
public record AgentShopPurchaseSequence<E extends AgentRuntimeHandle>(E entry,
                                        Character bot,
                                        Point npcPos,
                                        List<AgentShopPurchaseAction<E>> actions,
                                        List<String> bought,
                                        AgentShopBuyReport firstShortfall) {
    public AgentShopPurchaseSequence<E> withFirstShortfall(AgentShopBuyReport report) {
        if (firstShortfall == null && report != null && report.hasShortfall()) {
            return new AgentShopPurchaseSequence(entry, bot, npcPos, actions, bought, report);
        }
        return this;
    }
}
