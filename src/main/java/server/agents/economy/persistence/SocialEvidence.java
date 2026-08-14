package server.agents.economy.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SocialEvidence(UUID socialEventId, UUID runId, Instant logicalTime, int roomMapId,
                             String speakerAgentId, String targetAgentId, String eventKind,
                             String publicText, Map<String, Object> structuredIntent,
                             Integer relatedItemId, UUID relatedEconomicEventId) {
    public SocialEvidence {
        if (socialEventId == null || runId == null || logicalTime == null || roomMapId < 910000000
                || roomMapId > 910000022 || speakerAgentId == null || eventKind == null
                || publicText == null) throw new IllegalArgumentException();
        structuredIntent = structuredIntent == null ? Map.of() : Map.copyOf(structuredIntent);
    }
}
