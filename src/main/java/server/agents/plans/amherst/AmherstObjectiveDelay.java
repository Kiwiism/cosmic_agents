package server.agents.plans.amherst;

import config.YamlConfig;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface AmherstObjectiveDelay {
    AmherstObjectiveDelay NONE = () -> 0L;

    long nextDelayMs();

    static AmherstObjectiveDelay configured() {
        return () -> {
            int configuredMin = YamlConfig.config.server.AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MIN_MS;
            int configuredMax = YamlConfig.config.server.AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MAX_MS;
            int min = Math.max(0, configuredMin);
            int max = Math.max(min, configuredMax);
            if (max == 0) {
                return 0L;
            }
            return ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
        };
    }
}
