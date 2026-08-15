package server.agents.simulation.activity;

import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.NamedRandomStreams;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Rule-exact farming composition owned by scenario/activity tooling, outside economy sessions. */
public final class RuleExactExternalActivityAdapter implements ExternalAgentActivityPort {
    private final Planner planner;
    private final RuleExactFarmResolver resolver;
    private final Lifecycle lifecycle;

    public RuleExactExternalActivityAdapter(Planner planner, RuleExactFarmResolver resolver,
                                            Lifecycle lifecycle) {
        this.planner = Objects.requireNonNull(planner);
        this.resolver = Objects.requireNonNull(resolver);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }

    @Override public FarmSessionPlan plan(EconomyAgentProfile profile, Instant at) {
        return planner.plan(profile, at);
    }

    @Override public FarmSessionOutcome resolve(FarmSessionPlan plan, NamedRandomStreams random) {
        return resolver.resolve(plan, random);
    }

    @Override public void begin(EconomyAgentProfile profile, FarmSessionPlan plan, Instant at) {
        lifecycle.begin(profile, plan, at);
    }

    @Override public FarmSessionOutcome settle(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                               Instant at, LongSupplier random) {
        return lifecycle.settle(profile, outcome, at, random);
    }

    @Override public void returnToEconomyEntrance(EconomyAgentProfile profile, Instant at) {
        lifecycle.returnToEconomyEntrance(profile, at);
    }

    @Override public Map<String, Object> snapshotState() { return lifecycle.snapshotState(); }

    @Override public void restoreState(Map<String, Object> state,
                                       Map<String, EconomyAgentProfile> profiles) {
        lifecycle.restoreState(state, profiles);
    }

    @FunctionalInterface public interface Planner {
        FarmSessionPlan plan(EconomyAgentProfile profile, Instant logicalAt);
    }

    public interface Lifecycle {
        void begin(EconomyAgentProfile profile, FarmSessionPlan plan, Instant logicalAt);
        FarmSessionOutcome settle(EconomyAgentProfile profile, FarmSessionOutcome outcome,
                                  Instant logicalAt, LongSupplier deterministicGameplayRandom);
        void returnToEconomyEntrance(EconomyAgentProfile profile, Instant logicalAt);
        default Map<String, Object> snapshotState() { return Map.of(); }
        default void restoreState(Map<String, Object> state,
                                  Map<String, EconomyAgentProfile> profiles) { }
    }
}
