package server.security;

import java.util.Objects;
import java.util.UUID;

public final class SecurityEventReviewService {
    private SecurityEventReviewService() {
    }

    public static boolean markReviewed(UUID eventId, String reviewer, String note) {
        Objects.requireNonNull(eventId, "eventId");
        String normalizedReviewer = requireText(reviewer, "reviewer", 64);
        String normalizedNote = requireText(note, "note", 1024);
        return SecurityEventRuntime.markReviewed(eventId, normalizedReviewer, normalizedNote);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
