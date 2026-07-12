package server.agents.capabilities.objective;

import config.YamlConfig;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface AmherstNpcInteractionDelay {
    AmherstNpcInteractionDelay NONE = () -> 0L;

    long nextDelayMs();

    static AmherstNpcInteractionDelay configured() {
        return () -> {
            int configuredMin = YamlConfig.config.server.AGENT_AMHERST_NPC_INTERACTION_DELAY_MIN_MS;
            int configuredMax = YamlConfig.config.server.AGENT_AMHERST_NPC_INTERACTION_DELAY_MAX_MS;
            int min = Math.max(0, configuredMin);
            int max = Math.max(min, configuredMax);
            if (max == 0) {
                return 0L;
            }
            return ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
        };
    }
}
