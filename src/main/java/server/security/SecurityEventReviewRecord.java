package server.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SecurityEventReviewRecord(
        UUID eventId,
        Instant occurredAt,
        SecurityEventType type,
        SecuritySeverity severity,
        int accountId,
        int characterId,
        String remoteFingerprint,
        Map<String, String> evidence) {
    public SecurityEventReviewRecord {
        evidence = Map.copyOf(evidence);
    }
}
