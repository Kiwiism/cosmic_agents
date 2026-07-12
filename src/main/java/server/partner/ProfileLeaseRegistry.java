package server.partner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Process-local exclusivity registry for profile-to-session and actor-to-profile
 * attachments. Database login checks use the same registry while the server is
 * running; restart recovery closes journals and starts from canonical bindings.
 */
public final class ProfileLeaseRegistry {
    public static final int DETACHED_ACTOR = 0;
    private static final ProfileLeaseRegistry GLOBAL = new ProfileLeaseRegistry();

    private final Object lock = new Object();
    private final Map<Integer, ProfileLease> byProfileOwnerId = new HashMap<>();
    private final Map<Integer, ProfileLease> byActorCharacterId = new HashMap<>();
    private final Set<Integer> exclusiveReservations = new HashSet<>();

    public static ProfileLeaseRegistry global() {
        return GLOBAL;
    }

    public LeaseResult acquire(long sessionId, Map<Integer, Integer> profileToActor) {
        Objects.requireNonNull(profileToActor, "profileToActor");
        if (profileToActor.isEmpty()) {
            throw new IllegalArgumentException("At least one profile lease is required");
        }
        List<Map.Entry<Integer, Integer>> ordered = new ArrayList<>(profileToActor.entrySet());
        ordered.sort(Comparator.comparingInt(Map.Entry::getKey));
        if (ordered.stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() < DETACHED_ACTOR)) {
            throw new IllegalArgumentException("Profile IDs must be positive and actor IDs cannot be negative");
        }

        synchronized (lock) {
            for (Map.Entry<Integer, Integer> request : ordered) {
                if (exclusiveReservations.contains(request.getKey())) {
                    return LeaseResult.denied(
                            "Profile " + request.getKey() + " is entering the world");
                }
                ProfileLease existingProfile = byProfileOwnerId.get(request.getKey());
                if (existingProfile != null && existingProfile.sessionId() != sessionId) {
                    return LeaseResult.denied("Profile " + request.getKey() + " is already leased");
                }
                if (request.getValue() != DETACHED_ACTOR) {
                    ProfileLease existingActor = byActorCharacterId.get(request.getValue());
                    if (existingActor != null
                            && (existingActor.sessionId() != sessionId
                            || existingActor.profileOwnerCharacterId() != request.getKey())) {
                        return LeaseResult.denied("Actor " + request.getValue() + " already holds another profile");
                    }
                }
            }

            Instant acquiredAt = Instant.now();
            Map<Integer, ProfileLease> acquired = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> request : ordered) {
                ProfileLease lease = new ProfileLease(request.getKey(), request.getValue(), sessionId, acquiredAt);
                byProfileOwnerId.put(request.getKey(), lease);
                if (request.getValue() != DETACHED_ACTOR) {
                    byActorCharacterId.put(request.getValue(), lease);
                }
                acquired.put(request.getKey(), lease);
            }
            return LeaseResult.acquired(acquired);
        }
    }

    public LeaseResult rebind(long sessionId, Map<Integer, Integer> profileToActor) {
        Objects.requireNonNull(profileToActor, "profileToActor");
        synchronized (lock) {
            for (Integer profileOwnerId : profileToActor.keySet()) {
                ProfileLease existing = byProfileOwnerId.get(profileOwnerId);
                if (existing == null || existing.sessionId() != sessionId) {
                    return LeaseResult.denied("Session does not own profile " + profileOwnerId);
                }
            }
            Map<Integer, ProfileLease> originalProfiles = new HashMap<>(byProfileOwnerId);
            Map<Integer, ProfileLease> originalActors = new HashMap<>(byActorCharacterId);
            releaseSessionLocked(sessionId);
            LeaseResult result = acquire(sessionId, profileToActor);
            if (!result.acquired()) {
                byProfileOwnerId.clear();
                byProfileOwnerId.putAll(originalProfiles);
                byActorCharacterId.clear();
                byActorCharacterId.putAll(originalActors);
            }
            return result;
        }
    }

    public void releaseSession(long sessionId) {
        synchronized (lock) {
            releaseSessionLocked(sessionId);
        }
    }

    public boolean isLeased(int profileOwnerCharacterId) {
        synchronized (lock) {
            return byProfileOwnerId.containsKey(profileOwnerCharacterId);
        }
    }

    public boolean isUnavailable(int profileOwnerCharacterId) {
        synchronized (lock) {
            return byProfileOwnerId.containsKey(profileOwnerCharacterId)
                    || exclusiveReservations.contains(profileOwnerCharacterId);
        }
    }

    /** Serializes a normal character login against Partner activation. */
    public boolean tryReserveForLogin(int profileOwnerCharacterId) {
        if (profileOwnerCharacterId <= 0) {
            throw new IllegalArgumentException("Profile owner ID must be positive");
        }
        synchronized (lock) {
            if (byProfileOwnerId.containsKey(profileOwnerCharacterId)
                    || exclusiveReservations.contains(profileOwnerCharacterId)) {
                return false;
            }
            exclusiveReservations.add(profileOwnerCharacterId);
            return true;
        }
    }

    public void releaseLoginReservation(int profileOwnerCharacterId) {
        synchronized (lock) {
            exclusiveReservations.remove(profileOwnerCharacterId);
        }
    }

    /** Serializes character deletion against login and Partner activation. */
    public boolean tryReserveForDeletion(int profileOwnerCharacterId) {
        return tryReserveForLogin(profileOwnerCharacterId);
    }

    public void releaseDeletionReservation(int profileOwnerCharacterId) {
        releaseLoginReservation(profileOwnerCharacterId);
    }

    public boolean holds(long sessionId, int profileOwnerCharacterId) {
        synchronized (lock) {
            ProfileLease lease = byProfileOwnerId.get(profileOwnerCharacterId);
            return lease != null && lease.sessionId() == sessionId;
        }
    }

    public Optional<ProfileLease> leaseForProfile(int profileOwnerCharacterId) {
        synchronized (lock) {
            return Optional.ofNullable(byProfileOwnerId.get(profileOwnerCharacterId));
        }
    }

    public void clearForTests() {
        synchronized (lock) {
            byProfileOwnerId.clear();
            byActorCharacterId.clear();
            exclusiveReservations.clear();
        }
    }

    private void releaseSessionLocked(long sessionId) {
        List<ProfileLease> owned = byProfileOwnerId.values().stream()
                .filter(lease -> lease.sessionId() == sessionId)
                .toList();
        for (ProfileLease lease : owned) {
            byProfileOwnerId.remove(lease.profileOwnerCharacterId(), lease);
            if (lease.actorCharacterId() != DETACHED_ACTOR) {
                byActorCharacterId.remove(lease.actorCharacterId(), lease);
            }
        }
    }

    public record ProfileLease(int profileOwnerCharacterId,
                               int actorCharacterId,
                               long sessionId,
                               Instant acquiredAt) {
    }

    public record LeaseResult(boolean acquired,
                              String rejectionReason,
                              Map<Integer, ProfileLease> leases) {
        private static LeaseResult acquired(Map<Integer, ProfileLease> leases) {
            return new LeaseResult(true, null, Map.copyOf(leases));
        }

        private static LeaseResult denied(String reason) {
            return new LeaseResult(false, reason, Map.of());
        }
    }
}
