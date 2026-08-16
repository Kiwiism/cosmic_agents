package server.agents.economy.integration.cosmic;

import server.agents.economy.market.MarketObservation;

/** Replaceable rendering boundary. Structured offer data remains authoritative. */
@FunctionalInterface
public interface StallOfferTextRenderer {
    String render(MarketObservation listing, long offeredMesos);
}
