package server.agents.journey;

import java.util.Map;

/** Canonical append-only journey record; payloads remain transport-safe primitives. */
public record AgentJourneyEventRecord(
        int schemaVersion,
        String runId,
        long sequence,
        long occurredAtMs,
        int agentId,
        String agentName,
        String category,
        String eventType,
        String objectiveId,
        int mapId,
        boolean critical,
        Map<String, Object> attributes) {

    public AgentJourneyEventRecord {
        if (schemaVersion <= 0 || runId == null || runId.isBlank() || sequence <= 0
                || occurredAtMs < 0 || agentId <= 0 || category == null || category.isBlank()
                || eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Valid journey event identity is required");
        }
        agentName = agentName == null ? "" : agentName;
        objectiveId = objectiveId == null ? "" : objectiveId;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
