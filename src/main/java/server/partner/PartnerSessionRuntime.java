package server.partner;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** In-memory authoritative binding state for one active Partner session. */
public final class PartnerSessionRuntime {
    private final long sessionId;
    private final long linkId;
    private final int playerActorCharacterId;
    private final int partnerActorCharacterId;
    private final PartnerMode mode;
    private final ReentrantLock transitionLock = new ReentrantLock(true);

    private volatile ProfileBindings bindings;
    private volatile long generation;
    private volatile PartnerLifecycleStatus status;

    public PartnerSessionRuntime(long sessionId,
                                 long linkId,
                                 int playerActorCharacterId,
                                 int partnerActorCharacterId,
                                 int playerCanonicalProfileOwnerId,
                                 int partnerCanonicalProfileOwnerId,
                                 PartnerMode mode) {
        if (sessionId <= 0 || linkId <= 0 || playerActorCharacterId <= 0
                || playerCanonicalProfileOwnerId <= 0 || partnerCanonicalProfileOwnerId <= 0) {
            throw new IllegalArgumentException("Partner runtime identifiers must be positive");
        }
        if (mode == PartnerMode.DOUBLE_PARTNER && partnerActorCharacterId <= 0) {
            throw new IllegalArgumentException("Double Partner mode requires an Agent actor");
        }
        if (mode == PartnerMode.SOLO_TAG && partnerActorCharacterId != ProfileLeaseRegistry.DETACHED_ACTOR) {
            throw new IllegalArgumentException("Solo Tag mode cannot have a partner actor");
        }
        this.sessionId = sessionId;
        this.linkId = linkId;
        this.playerActorCharacterId = playerActorCharacterId;
        this.partnerActorCharacterId = partnerActorCharacterId;
        this.mode = mode;
        this.bindings = new ProfileBindings(
                playerCanonicalProfileOwnerId,
                partnerCanonicalProfileOwnerId,
                ProfileOrientation.CANONICAL);
        this.status = PartnerLifecycleStatus.ACTIVATING;
    }

    public void activate() {
        transitionLock.lock();
        try {
            requireStatus(PartnerLifecycleStatus.ACTIVATING);
            status = PartnerLifecycleStatus.ACTIVE;
        } finally {
            transitionLock.unlock();
        }
    }

    public TransitionToken beginSwap(long expectedGeneration) {
        transitionLock.lock();
        try {
            requireStatus(PartnerLifecycleStatus.ACTIVE);
            if (generation != expectedGeneration) {
                throw new IllegalStateException(
                        "Stale Partner generation " + expectedGeneration + "; current generation is " + generation);
            }
            generation++;
            status = PartnerLifecycleStatus.SWAPPING;
            return new TransitionToken(sessionId, generation, bindings, bindings.reversed());
        } finally {
            transitionLock.unlock();
        }
    }

    public void commitSwap(TransitionToken token) {
        transitionLock.lock();
        try {
            validateCurrentToken(token);
            bindings = token.after();
            status = PartnerLifecycleStatus.ACTIVE;
        } finally {
            transitionLock.unlock();
        }
    }

    public void abortSwap(TransitionToken token) {
        transitionLock.lock();
        try {
            validateCurrentToken(token);
            status = PartnerLifecycleStatus.ACTIVE;
        } finally {
            transitionLock.unlock();
        }
    }

    public long beginRelease() {
        transitionLock.lock();
        try {
            requireStatus(PartnerLifecycleStatus.ACTIVE);
            generation++;
            status = PartnerLifecycleStatus.RELEASING;
            return generation;
        } finally {
            transitionLock.unlock();
        }
    }

    public void restoreCanonicalForRelease(long releaseGeneration) {
        transitionLock.lock();
        try {
            requireStatus(PartnerLifecycleStatus.RELEASING);
            if (generation != releaseGeneration) {
                throw new IllegalStateException("Stale Partner release generation");
            }
            if (bindings.orientation() == ProfileOrientation.SWAPPED) {
                bindings = bindings.reversed();
            }
        } finally {
            transitionLock.unlock();
        }
    }

    public void abortRelease(long releaseGeneration) {
        transitionLock.lock();
        try {
            requireStatus(PartnerLifecycleStatus.RELEASING);
            if (generation != releaseGeneration) {
                throw new IllegalStateException("Stale Partner release generation");
            }
            if (bindings.orientation() != ProfileOrientation.CANONICAL) {
                throw new IllegalStateException("Partner release cannot be retried outside canonical orientation");
            }
            status = PartnerLifecycleStatus.ACTIVE;
        } finally {
            transitionLock.unlock();
        }
    }

    public void close(long releaseGeneration, PartnerLifecycleStatus terminalStatus) {
        transitionLock.lock();
        try {
            if (!terminalStatus.isTerminal()) {
                throw new IllegalArgumentException("Partner close status must be terminal");
            }
            if (status != PartnerLifecycleStatus.RELEASING || generation != releaseGeneration) {
                throw new IllegalStateException("Partner session is not in the expected release generation");
            }
            if (bindings.orientation() != ProfileOrientation.CANONICAL) {
                throw new IllegalStateException("Partner session cannot close while profiles are swapped");
            }
            status = terminalStatus;
        } finally {
            transitionLock.unlock();
        }
    }

    public boolean isCurrentGeneration(long expectedGeneration) {
        return generation == expectedGeneration && !status.isTerminal();
    }

    public Map<Integer, Integer> profileToActorLeases() {
        return profileToActorLeases(bindings);
    }

    public Map<Integer, Integer> profileToActorLeases(ProfileBindings selectedBindings) {
        ProfileBindings current = selectedBindings;
        if (mode == PartnerMode.SOLO_TAG) {
            return Map.of(
                    current.playerActorProfileOwnerId(), playerActorCharacterId,
                    current.partnerSlotProfileOwnerId(), ProfileLeaseRegistry.DETACHED_ACTOR);
        }
        return Map.of(
                current.playerActorProfileOwnerId(), playerActorCharacterId,
                current.partnerSlotProfileOwnerId(), partnerActorCharacterId);
    }

    public long sessionId() {
        return sessionId;
    }

    public long linkId() {
        return linkId;
    }

    public int playerActorCharacterId() {
        return playerActorCharacterId;
    }

    public int partnerActorCharacterId() {
        return partnerActorCharacterId;
    }

    public PartnerMode mode() {
        return mode;
    }

    public ProfileBindings bindings() {
        return bindings;
    }

    public long generation() {
        return generation;
    }

    public PartnerLifecycleStatus status() {
        return status;
    }

    private void validateCurrentToken(TransitionToken token) {
        requireStatus(PartnerLifecycleStatus.SWAPPING);
        if (token == null || token.sessionId() != sessionId || token.generation() != generation
                || !bindings.equals(token.before())) {
            throw new IllegalStateException("Stale or foreign Partner transition token");
        }
    }

    private void requireStatus(PartnerLifecycleStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected Partner status " + expected + " but was " + status);
        }
    }

    public record ProfileBindings(int playerActorProfileOwnerId,
                                  int partnerSlotProfileOwnerId,
                                  ProfileOrientation orientation) {
        public ProfileBindings reversed() {
            return new ProfileBindings(
                    partnerSlotProfileOwnerId,
                    playerActorProfileOwnerId,
                    orientation.reversed());
        }
    }

    public record TransitionToken(long sessionId,
                                  long generation,
                                  ProfileBindings before,
                                  ProfileBindings after) {
    }
}
