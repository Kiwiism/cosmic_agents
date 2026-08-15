package server.agents.runtime.townlife;

import server.agents.events.AgentContextualEvent;

/** Structured observation fact for an external cyclic TownLife test. */
public record AgentTownLifeTestScenarioEvent(
        int agentId,
        long occurredAtMs,
        String scenarioId,
        int cycle,
        Phase phase,
        String detail) implements AgentContextualEvent {

    public static final String TYPE = "townlife.test-scenario";

    public AgentTownLifeTestScenarioEvent {
        scenarioId = scenarioId == null ? "" : scenarioId.trim();
        detail = detail == null ? "" : detail.trim();
        if (agentId <= 0 || occurredAtMs < 0L || scenarioId.isBlank()
                || cycle < 0 || phase == null) {
            throw new IllegalArgumentException("valid TownLife test scenario event is required");
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String objectiveId() {
        return "";
    }

    @Override
    public String dedupeKey() {
        return scenarioId + ':' + cycle + ':' + phase;
    }

    public enum Phase {
        STARTED_VISIT,
        EXITED_VISIT,
        STAGING,
        OUTSIDE_IDLE,
        REENTERING,
        COMPLETED,
        FAILED,
        STOP_REQUESTED
    }
}
