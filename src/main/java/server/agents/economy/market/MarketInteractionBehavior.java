package server.agents.economy.market;

import client.Character;

import java.awt.Point;

/** Optional presentation policy for a stall already selected by the Commerce itinerary. */
public interface MarketInteractionBehavior {
    Point approachPoint(Character agent, FreeMarketPhysicalGateway.StallTarget stall);

    static MarketInteractionBehavior disabled() {
        return (agent, stall) -> new Point(stall.x(), stall.y());
    }
}
