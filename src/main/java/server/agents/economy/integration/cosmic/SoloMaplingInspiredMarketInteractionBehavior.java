package server.agents.economy.integration.cosmic;

import client.Character;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.agents.economy.market.MarketInteractionBehavior;

import java.awt.Point;

/**
 * Replaceable visual layer inspired by SoloMapling's locality-aware FM walks.
 * It never decides demand, value, inventory, or settlement.
 */
public final class SoloMaplingInspiredMarketInteractionBehavior implements MarketInteractionBehavior {
    private final int approachJitterPixels;

    public SoloMaplingInspiredMarketInteractionBehavior(int approachJitterPixels) {
        if (approachJitterPixels < 0) throw new IllegalArgumentException("approach jitter cannot be negative");
        this.approachJitterPixels = approachJitterPixels;
    }

    @Override
    public Point approachPoint(Character agent, FreeMarketPhysicalGateway.StallTarget stall) {
        if (approachJitterPixels == 0) return new Point(stall.x(), stall.y());
        int width = Math.addExact(Math.multiplyExact(approachJitterPixels, 2), 1);
        int seed = 31 * agent.getId() + stall.objectId();
        int offset = Math.floorMod(seed, width) - approachJitterPixels;
        return new Point(stall.x() + offset, stall.y());
    }
}
