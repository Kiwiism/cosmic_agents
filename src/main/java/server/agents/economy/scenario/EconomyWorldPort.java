package server.agents.economy.scenario;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;

import java.time.Instant;
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

    record MarketDirective(Optional<Instant> startActivityAt, Optional<Instant> revisitMarketAt) {
        public MarketDirective {
            startActivityAt = startActivityAt == null ? Optional.empty() : startActivityAt;
            revisitMarketAt = revisitMarketAt == null ? Optional.empty() : revisitMarketAt;
        }

        public static MarketDirective idle() {
            return new MarketDirective(Optional.empty(), Optional.empty());
        }
    }
}
