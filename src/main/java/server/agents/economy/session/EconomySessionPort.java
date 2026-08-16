package server.agents.economy.session;

import server.agents.economy.scenario.EconomyAgentProfile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Standalone ownership boundary for one bounded economic visit.
 *
 * <p>The caller owns travel and all activity outside the visit. Implementations may inspect and
 * command economic behavior only between an accepted entry and a successful release.</p>
 */
public interface EconomySessionPort {
    EntryResult requestEntry(EconomyAgentProfile profile, EntryRequest request, Instant logicalAt);

    Directive performMarketCycle(UUID sessionId, EconomyAgentProfile profile, Instant logicalAt);

    ReleaseResult release(UUID sessionId, EconomyAgentProfile profile, Instant logicalAt, String reason);

    default Map<String, Object> snapshotState() { return Map.of(); }

    default void restoreState(Map<String, Object> state) {
        if (state != null && !state.isEmpty())
            throw new IllegalStateException("economy session adapter does not support checkpoint state");
    }

    default void restoreState(Map<String, Object> state, Map<String, EconomyAgentProfile> profiles) {
        restoreState(state);
    }

    default Optional<Presence> sessionPresence(EconomyAgentProfile profile) { return Optional.empty(); }

    record EntryRequest(UUID requestId, String reason, Duration maximumDuration,
                        Duration maximumIdleDuration, Map<String, String> attributes) {
        public EntryRequest {
            if (requestId == null || reason == null || reason.isBlank() || maximumDuration == null
                    || maximumDuration.isZero() || maximumDuration.isNegative()
                    || maximumIdleDuration == null || maximumIdleDuration.isNegative()
                    || maximumIdleDuration.compareTo(maximumDuration) > 0)
                throw new IllegalArgumentException("invalid economy entry request");
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public static EntryRequest scheduled(UUID runId, String agentId, Instant requestedAt,
                                             Duration maximumDuration,
                                             Duration maximumIdleDuration) {
            UUID id = UUID.nameUUIDFromBytes((runId + ":" + agentId + ":economy-entry:" + requestedAt)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new EntryRequest(id, "SCHEDULED_SCENARIO_ENTRY", maximumDuration,
                    maximumIdleDuration, Map.of());
        }
    }

    record EntryResult(Status status, UUID sessionId, String reason,
                       Instant retryAt, Instant expiresAt) {
        public EntryResult {
            Objects.requireNonNull(status); reason = reason == null ? "" : reason;
            if (status == Status.ACCEPTED && (sessionId == null || expiresAt == null))
                throw new IllegalArgumentException("accepted economy entry requires a session and expiry");
            if (status == Status.DEFERRED && retryAt == null)
                throw new IllegalArgumentException("deferred economy entry requires retryAt");
            if (status != Status.ACCEPTED && sessionId != null)
                throw new IllegalArgumentException("non-accepted economy entry cannot own a session");
        }

        public static EntryResult accepted(UUID sessionId, Instant expiresAt, String reason) {
            return new EntryResult(Status.ACCEPTED, sessionId, reason, null, expiresAt);
        }

        public static EntryResult deferred(String reason, Instant retryAt) {
            return new EntryResult(Status.DEFERRED, null, reason, retryAt, null);
        }

        public static EntryResult rejected(String reason) {
            return new EntryResult(Status.REJECTED, null, reason, null, null);
        }

        public enum Status { ACCEPTED, DEFERRED, REJECTED }
    }

    /** A market tick either requests another tick, requests release, or waits for external I/O. */
    record Directive(Optional<Instant> revisitAt, boolean releaseRequested,
                     Optional<Instant> outsideAvailableAt, boolean externalActionPending,
                     String reason) {
        public Directive {
            revisitAt = revisitAt == null ? Optional.empty() : revisitAt;
            outsideAvailableAt = outsideAvailableAt == null ? Optional.empty() : outsideAvailableAt;
            reason = reason == null ? "" : reason;
            if (releaseRequested && revisitAt.isPresent())
                throw new IllegalArgumentException("release directive cannot also revisit");
            if (!releaseRequested && outsideAvailableAt.isPresent())
                throw new IllegalArgumentException("only a release may expose outside availability");
        }

        public static Directive revisit(Instant at, boolean externalPending, String reason) {
            return new Directive(Optional.of(at), false, Optional.empty(), externalPending, reason);
        }

        public static Directive release(Instant outsideAvailableAt, String reason) {
            return new Directive(Optional.empty(), true, Optional.of(outsideAvailableAt), false, reason);
        }

        public static Directive waiting(String reason) {
            return new Directive(Optional.empty(), false, Optional.empty(), false, reason);
        }
    }

    record ReleaseResult(Status status, String reason, Instant retryAt) {
        public ReleaseResult {
            Objects.requireNonNull(status); reason = reason == null ? "" : reason;
            if (status == Status.DEFERRED && retryAt == null)
                throw new IllegalArgumentException("deferred release requires retryAt");
        }

        public static ReleaseResult released(String reason) {
            return new ReleaseResult(Status.RELEASED, reason, null);
        }

        public static ReleaseResult deferred(String reason, Instant retryAt) {
            return new ReleaseResult(Status.DEFERRED, reason, retryAt);
        }

        public static ReleaseResult rejected(String reason) {
            return new ReleaseResult(Status.REJECTED, reason, null);
        }

        public enum Status { RELEASED, DEFERRED, REJECTED }
    }

    record Presence(int mapId, int x, int y, boolean visible) { }
}
