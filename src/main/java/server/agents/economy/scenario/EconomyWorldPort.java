package server.agents.economy.scenario;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Replaceable live/headless boundary. Implementations must use real Cosmic rules and holdings. */
public interface EconomyWorldPort {
    void admit(EconomyAgentProfile profile, Instant logicalAt);

    MarketDirective performMarketCycle(EconomyAgentProfile profile, Instant logicalAt);

    FarmSessionPlan planOffscreenActivity(EconomyAgentProfile profile, Instant logicalAt);

    void leaveFreeMarket(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt);

    void settleOffscreenActivity(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                 Instant logicalAt, LongSupplier deterministicGameplayRandom);

    void returnThroughFreeMarketEntrance(EconomyAgentProfile profile, Instant logicalAt);

    /** Serializable adapter-owned state that must advance atomically with the logical checkpoint. */
    default Map<String, Object> snapshotState() { return Map.of(); }

    /** Restores adapter-owned state after Cosmic characters and durable stores are available. */
    default void restoreState(Map<String, Object> state) {
        if (state != null && !state.isEmpty())
            throw new IllegalStateException("world adapter does not support checkpoint state");
    }

    default Optional<Presence> currentPresence(EconomyAgentProfile profile) { return Optional.empty(); }

    record Presence(int mapId, int x, int y, boolean visible) { }

    record MarketDirective(Optional<Instant> startActivityAt, Optional<Instant> revisitMarketAt,
                           boolean externalActionPending) {
        public MarketDirective(Optional<Instant> startActivityAt, Optional<Instant> revisitMarketAt) {
            this(startActivityAt, revisitMarketAt, false);
        }
        public MarketDirective {
            startActivityAt = startActivityAt == null ? Optional.empty() : startActivityAt;
            revisitMarketAt = revisitMarketAt == null ? Optional.empty() : revisitMarketAt;
        }

        public static MarketDirective idle() {
            return new MarketDirective(Optional.empty(), Optional.empty(), false);
        }
    }
}
