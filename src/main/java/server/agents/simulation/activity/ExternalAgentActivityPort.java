package server.agents.simulation.activity;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.NamedRandomStreams;

import java.time.Instant;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Activity-side contract used by scenario tooling. The economy session never calls this port.
 * A real autonomous runtime may replace it entirely and later request a new economy admission.
 */
public interface ExternalAgentActivityPort {
    FarmSessionPlan plan(EconomyAgentProfile profile, Instant logicalAt);

    FarmSessionOutcome resolve(FarmSessionPlan plan, NamedRandomStreams randomStreams);

    void begin(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt);

    FarmSessionOutcome settle(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                              Instant logicalAt, LongSupplier deterministicGameplayRandom);

    void returnToEconomyEntrance(EconomyAgentProfile profile, Instant logicalAt);

    default Map<String, Object> snapshotState() { return Map.of(); }

    default void restoreState(Map<String, Object> state,
                              Map<String, EconomyAgentProfile> profiles) {
        if (state != null && !state.isEmpty())
            throw new IllegalStateException("external activity adapter does not support checkpoint state");
    }
}
