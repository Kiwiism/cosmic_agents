package server.agents.economy.scenario;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Converts a whole-population FM target into the required share of commerce-eligible cohorts. */
public final class PopulationMarketParticipationSchedule {
    private final List<Entry> entries;

    public PopulationMarketParticipationSchedule(EconomyEngineConfig.Population population,
                                                 Instant runStart, long seed) {
        Objects.requireNonNull(population); Objects.requireNonNull(runStart);
        Duration onboarding = Duration.parse(population.onboardingDuration);
        entries = new PopulationAdmissionPlanner().plan(population, runStart, new NamedRandomStreams(seed))
                .stream().map(value -> new Entry(value.agentId(), value.admittedAt(),
                        value.admittedAt().plus(onboarding))).toList();
    }

    /** Empty means this agent is still in its non-commerce onboarding window. */
    public OptionalDouble eligibleTarget(String agentId, Instant at, double wholePopulationTarget) {
        Entry subject = entries.stream().filter(value -> value.agentId().equals(agentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown population agent " + agentId));
        if (at.isBefore(subject.eligibleAt)) return OptionalDouble.empty();
        long population = entries.stream().filter(value -> !value.admittedAt.isAfter(at)).count();
        long eligible = entries.stream().filter(value -> !value.eligibleAt.isAfter(at)).count();
        if (eligible == 0) return OptionalDouble.empty();
        return OptionalDouble.of(Math.min(.99d, wholePopulationTarget * population / eligible));
    }

    private record Entry(String agentId, Instant admittedAt, Instant eligibleAt) { }
}
