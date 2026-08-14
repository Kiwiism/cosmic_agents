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
            if (count <= 0) break;
            Instant admittedAt = runStart.plus(Duration.ofDays((long) day * config.growth.everyLogicalDays));
            for (int i = 0; i < count; i++) {
                int ordinal = population + i + 1;
                String job = weightedChoice(config.classDistribution, random.stream("population.class"));
                double activity = weightedActivity(config.activityDistribution,
                        random.stream("population.activity"));
                admissions.add(new Admission("agent-" + ordinal, admittedAt, job, activity));
            }
            population += count;
            day++;
        }
        return List.copyOf(admissions);
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

    public record Admission(String agentId, Instant admittedAt, String jobFamily,
                            double dailyActivityFraction) { }
}
