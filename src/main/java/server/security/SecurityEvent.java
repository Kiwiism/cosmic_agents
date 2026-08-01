package server.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SecurityEvent(
        long sequence,
        UUID eventId,
        Instant occurredAt,
        SecurityEventType type,
        SecuritySeverity severity,
        int accountId,
        int characterId,
        String remoteFingerprint,
        Map<String, String> evidence) {

    public SecurityEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        evidence = Map.copyOf(evidence);
    }
}
