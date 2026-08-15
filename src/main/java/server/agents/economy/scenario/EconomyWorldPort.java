package server.agents.economy.scenario;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.session.EconomySessionPort;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Replaceable live/headless boundary. Implementations must use real Cosmic rules and holdings. */
/**
 * @deprecated Composite scenario adapter retained only for fast-forward compatibility. Live
 * autonomous callers should use {@link EconomySessionPort}; external activity owns its own port.
 */
@Deprecated
public interface EconomyWorldPort extends EconomySessionPort {
    void admit(EconomyAgentProfile profile, Instant logicalAt);

    MarketDirective performMarketCycle(EconomyAgentProfile profile, Instant logicalAt);

    FarmSessionPlan planOffscreenActivity(EconomyAgentProfile profile, Instant logicalAt);

    void leaveFreeMarket(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt);

    FarmSessionOutcome settleOffscreenActivity(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                               Instant logicalAt, LongSupplier deterministicGameplayRandom);

    void returnThroughFreeMarketEntrance(EconomyAgentProfile profile, Instant logicalAt);

    @Override
    default EntryResult requestEntry(EconomyAgentProfile profile, EntryRequest request, Instant logicalAt) {
        admit(profile, logicalAt);
        java.util.UUID sessionId = java.util.UUID.nameUUIDFromBytes((profile.agentId() + ':'
                + request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return EntryResult.accepted(sessionId, logicalAt.plus(request.maximumDuration()),
                "LEGACY_SCENARIO_ADAPTER");
    }

    @Override
    default Directive performMarketCycle(java.util.UUID sessionId, EconomyAgentProfile profile,
                                         Instant logicalAt) {
        MarketDirective value = performMarketCycle(profile, logicalAt);
        if (value.startActivityAt().isPresent())
            return Directive.release(value.startActivityAt().orElseThrow(), "MARKET_GOALS_COMPLETE");
        return value.revisitMarketAt().map(at -> Directive.revisit(at,
                        value.externalActionPending(), "ECONOMIC_WORK_REMAINS"))
                .orElseGet(() -> Directive.waiting("LEGACY_IDLE"));
    }

    @Override
    default ReleaseResult release(java.util.UUID sessionId, EconomyAgentProfile profile,
                                  Instant logicalAt, String reason) {
        return ReleaseResult.released(reason);
    }

    /** Serializable adapter-owned state that must advance atomically with the logical checkpoint. */
    default Map<String, Object> snapshotState() { return Map.of(); }

    /** Restores adapter-owned state after Cosmic characters and durable stores are available. */
    default void restoreState(Map<String, Object> state) {
        if (state != null && !state.isEmpty())
            throw new IllegalStateException("world adapter does not support checkpoint state");
    }

    /** Restores adapter state with the checkpoint's authoritative admitted profiles. */
    default void restoreState(Map<String, Object> state, Map<String, EconomyAgentProfile> profiles) {
        restoreState(state);
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
