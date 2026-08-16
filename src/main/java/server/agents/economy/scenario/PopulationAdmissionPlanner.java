package server.agents.economy.scenario;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Produces deterministic admissions from configured initial, daily-growth, and cap values. */
public final class PopulationAdmissionPlanner {
    public List<Admission> plan(EconomyEngineConfig.Population config, Instant runStart,
                                NamedRandomStreams random) {
        List<Admission> admissions = new ArrayList<>();
        int population = 0;
        int day = 0;
        while (population < config.maximumAgents) {
            int count = day == 0 ? config.initialAgents
                    : Math.min(config.growth.amount, config.maximumAgents - population);
            if (count <= 0) {
                day++;
                continue;
            }
            for (int i = 0; i < count; i++) {
                Instant admittedAt = admissionTime(config, runStart, day, i, count);
                int ordinal = population + i + 1;
                String job = weightedChoice(config.classDistribution, random.stream("population.class"));
                double activity = weightedActivity(config.activityDistribution,
                        random.stream("population.activity"));
                NamedRandomStreams.Stream traits = random.stream("population.profile");
                EconomyAgentProfile profile = new EconomyAgentProfile("agent-" + ordinal, job, activity,
                        traits.nextDouble(), traits.nextDouble(), traits.nextDouble(), traits.nextDouble(),
                        sellerWillingness(config, traits), 6 + traits.nextInt(67), traits.nextDouble(),
                        traits.nextDouble());
                admissions.add(new Admission(profile, admittedAt));
            }
            population += count;
            day++;
        }
        return List.copyOf(admissions);
    }

    private static double sellerWillingness(EconomyEngineConfig.Population config,
                                             NamedRandomStreams.Stream random) {
        return random.nextDouble() < config.merchantParticipation.willingSellerFraction
                ? 0.5 + random.nextDouble() * 0.5 : random.nextDouble() * 0.25;
    }

    private static Instant admissionTime(EconomyEngineConfig.Population config, Instant runStart,
                                         int day, int index, int count) {
        Duration interval = Duration.ofDays(config.growth.everyLogicalDays);
        Instant boundary = runStart.plus(interval.multipliedBy(day));
        if (day == 0 || !config.growth.spreadArrivalsAcrossInterval) return boundary;
        long offsetNanos = Math.multiplyExact(interval.toNanos(), 2L * index + 1) / (2L * count);
        return boundary.plusNanos(offsetNanos);
    }

    private static String weightedChoice(Map<String, Double> weights, NamedRandomStreams.Stream random) {
        double draw = random.nextDouble();
        double cumulative = 0;
        String last = null;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            last = entry.getKey();
            cumulative += entry.getValue();
            if (draw < cumulative) return entry.getKey();
        }
        return last;
    }

    private static double weightedActivity(Map<String, Double> weights, NamedRandomStreams.Stream random) {
        return switch (weightedChoice(weights, random)) {
            case "casual" -> 0.35;
            case "intensive" -> 0.90;
            default -> 0.65;
        };
    }

    public record Admission(EconomyAgentProfile profile, Instant admittedAt) {
        public String agentId() { return profile.agentId(); }
        public String jobFamily() { return profile.jobFamily(); }
        public double dailyActivityFraction() { return profile.dailyActivityFraction(); }
    }
}
