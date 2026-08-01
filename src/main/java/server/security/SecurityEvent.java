package server.security;

import java.time.Instant;
import java.util.Map;

public record SecurityEvent(
        long sequence,
        Instant occurredAt,
        SecurityEventType type,
        SecuritySeverity severity,
        int accountId,
        int characterId,
        String remoteFingerprint,
        Map<String, String> evidence) {

    public SecurityEvent {
        evidence = Map.copyOf(evidence);
    }
}
