package server.agents.journey;

/** Tunable operational limits for bounded Agent journey evidence collection. */
public record AgentJourneyConfig(
        boolean enabled,
        int queueCapacity,
        long sampleIntervalMs,
        long suspicionAfterMs,
        long mapDwellAfterMs,
        long flightRecorderWindowMs,
        int loopTransitionCount,
        int maxDetailedAgents) {

    public static AgentJourneyConfig configured() {
        return new AgentJourneyConfig(
                config.AgentTuning.booleanValue("server.agents.journey.AgentJourneyConfig.ENABLED"),
                config.AgentTuning.intValue("server.agents.journey.AgentJourneyConfig.QUEUE_CAPACITY"),
                config.AgentTuning.longValue("server.agents.journey.AgentJourneyConfig.SAMPLE_INTERVAL_MS"),
                config.AgentTuning.longValue("server.agents.journey.AgentJourneyConfig.SUSPICION_AFTER_MS"),
                config.AgentTuning.longValue("server.agents.journey.AgentJourneyConfig.MAP_DWELL_AFTER_MS"),
                config.AgentTuning.longValue("server.agents.journey.AgentJourneyConfig.FLIGHT_RECORDER_WINDOW_MS"),
                config.AgentTuning.intValue("server.agents.journey.AgentJourneyConfig.LOOP_TRANSITION_COUNT"),
                config.AgentTuning.intValue("server.agents.journey.AgentJourneyConfig.MAX_DETAILED_AGENTS"));
    }

    public AgentJourneyConfig {
        if (queueCapacity < 128 || sampleIntervalMs < 1_000L || suspicionAfterMs < sampleIntervalMs
                || mapDwellAfterMs < suspicionAfterMs || flightRecorderWindowMs < sampleIntervalMs
                || loopTransitionCount < 4 || maxDetailedAgents < 1) {
            throw new IllegalArgumentException("Invalid Agent journey observability configuration");
        }
    }
}
