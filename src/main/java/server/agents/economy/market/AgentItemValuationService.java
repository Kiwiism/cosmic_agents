package server.agents.economy.market;

import java.time.Instant;

/** Stable economy-owned query boundary for agents and inventory disposition wrappers. */
public interface AgentItemValuationService {
    Valuation value(String agentId, int itemId, Instant logicalAt);

    record Valuation(long unitValueMesos, Source source, long observedMedianMesos,
                     int observationCount, long catalogAnchorMesos, String overrideReason) {
        public enum Source { CUSTOM_OVERRIDE, PRIVATE_OBSERVATIONS, CATALOG_ANCHOR, UNKNOWN }
        public Valuation {
            if (unitValueMesos < 0 || observedMedianMesos < 0 || observationCount < 0
                    || catalogAnchorMesos < 0 || source == null)
                throw new IllegalArgumentException("invalid item valuation");
            overrideReason = overrideReason == null ? "" : overrideReason;
        }
    }

    static AgentItemValuationService unknown() {
        return (agentId, itemId, logicalAt) -> new Valuation(0, Valuation.Source.UNKNOWN,
                0, 0, 0, "");
    }
}
