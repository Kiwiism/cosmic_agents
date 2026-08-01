package server.security;

import java.util.Objects;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class SecurityEventReviewService {
    private SecurityEventReviewService() {
    }

    public static boolean markReviewed(UUID eventId, String reviewer, String note) {
        Objects.requireNonNull(eventId, "eventId");
        String normalizedReviewer = requireText(reviewer, "reviewer", 64);
        String normalizedNote = requireText(note, "note", 1024);
        return SecurityEventRuntime.markReviewed(eventId, normalizedReviewer, normalizedNote);
    }

    public static List<SecurityEventReviewRecord> findOpen(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return SecurityEventRuntime.findOpen(limit);
    }

    public static int applyRetention(Duration reviewedRetention) {
        Objects.requireNonNull(reviewedRetention, "reviewedRetention");
        if (reviewedRetention.isNegative() || reviewedRetention.isZero()) {
            throw new IllegalArgumentException("reviewedRetention must be positive");
        }
        return SecurityEventRuntime.deleteReviewedBefore(Instant.now().minus(reviewedRetention));
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
