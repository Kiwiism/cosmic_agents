package server.security;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

interface SecurityEventStore {
    void append(SecurityEvent event);

    boolean markReviewed(UUID eventId, String reviewer, String note);

    List<SecurityEventReviewRecord> findOpen(int limit);

    int deleteReviewedBefore(Instant cutoff);
}
