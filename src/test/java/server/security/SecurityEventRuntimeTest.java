package server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventRuntimeTest {
    @AfterEach
    void clear() {
        SecurityEventRuntime.clearForTesting();
    }

    @Test
    void retainsStructuredEvidenceWithoutRawRemoteAddress() {
        SecurityEvent event = SecurityEventRuntime.recordExternal(SecurityEventType.MALFORMED_PACKET,
                SecuritySeverity.WARNING, "203.0.113.42", Map.of("opcode", "123"));

        assertEquals("123", event.evidence().get("opcode"));
        assertNotEquals("203.0.113.42", event.remoteFingerprint());
        assertTrue(SecurityEventRuntime.snapshot().contains(event));
    }

    @Test
    void deliversCriticalEventsToTheInstalledAlertSink() {
        AtomicReference<SecurityEvent> delivered = new AtomicReference<>();
        SecurityEventRuntime.installAlertSink(delivered::set);

        SecurityEvent event = SecurityEventRuntime.recordExternal(SecurityEventType.ECONOMY_INVARIANT,
                SecuritySeverity.CRITICAL, "203.0.113.43", Map.of("transaction", "abc"));

        assertEquals(event, delivered.get());
    }

    @Test
    void reviewServiceQueriesAndPurgesThePersistentStore() {
        TrackingStore store = new TrackingStore();
        SecurityEventRuntime.installStoreForTesting(store);

        assertEquals(store.events, SecurityEventReviewService.findOpen(10));
        assertEquals(3, SecurityEventReviewService.applyRetention(java.time.Duration.ofDays(90)));
        assertTrue(store.cutoff.isBefore(Instant.now().minus(java.time.Duration.ofDays(89))));
    }

    private static final class TrackingStore implements SecurityEventStore {
        private final List<SecurityEventReviewRecord> events = new ArrayList<>();
        private Instant cutoff;

        @Override public void append(SecurityEvent event) { }
        @Override public boolean markReviewed(UUID eventId, String reviewer, String note) { return true; }
        @Override public List<SecurityEventReviewRecord> findOpen(int limit) { return events; }
        @Override public int deleteReviewedBefore(Instant cutoff) { this.cutoff = cutoff; return 3; }
    }
}
