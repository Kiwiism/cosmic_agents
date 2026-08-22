package server.agents.journey;

import java.util.List;
import java.util.Set;

/** Durable identity and cohort membership for one bounded progression experiment. */
public record AgentJourneyManifest(
        int schemaVersion,
        String runId,
        String scenarioId,
        String simulationMode,
        long startedAtMs,
        int targetLevel,
        List<Participant> participants) {

    public AgentJourneyManifest {
        simulationMode = simulationMode == null ? "" : simulationMode;
        if (schemaVersion <= 0 || runId == null || runId.isBlank()
                || scenarioId == null || scenarioId.isBlank() || startedAtMs < 0
                || targetLevel <= 0
                || !Set.of("off", "light", "full", "decisions").contains(simulationMode)) {
            throw new IllegalArgumentException("Valid journey manifest identity is required");
        }
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    public record Participant(int characterId, String characterName, String career) {
        public Participant {
            characterName = characterName == null ? "" : characterName;
            career = career == null ? "" : career;
            if (characterId <= 0 || characterName.isBlank() || career.isBlank()) {
                throw new IllegalArgumentException("Valid journey participant identity is required");
            }
        }
    }
}
