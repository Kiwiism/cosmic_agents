package server.security;

import java.util.UUID;

interface SecurityEventStore {
    void append(SecurityEvent event);

    boolean markReviewed(UUID eventId, String reviewer, String note);
}
