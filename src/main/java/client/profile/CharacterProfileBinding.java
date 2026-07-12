package client.profile;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owner identity and mutation version for the profile currently attached to a
 * live Character actor. The actor's canonical ID is intentionally separate.
 */
public final class CharacterProfileBinding {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final AtomicLong version = new AtomicLong();
    private volatile int ownerCharacterId;
    private volatile long generation;

    public void initializeCanonicalOwner(int characterId) {
        if (characterId <= 0) {
            throw new IllegalArgumentException("Canonical profile owner ID must be positive");
        }
        lock.lock();
        try {
            if (ownerCharacterId != 0 && ownerCharacterId != characterId) {
                throw new IllegalStateException("Profile owner is already initialized");
            }
            ownerCharacterId = characterId;
        } finally {
            lock.unlock();
        }
    }

    public long rebind(int expectedOwnerCharacterId,
                       int newOwnerCharacterId,
                       long expectedGeneration) {
        if (newOwnerCharacterId <= 0) {
            throw new IllegalArgumentException("Profile owner ID must be positive");
        }
        lock.lock();
        try {
            if (ownerCharacterId != expectedOwnerCharacterId || generation != expectedGeneration) {
                throw new IllegalStateException("Stale profile binding transition");
            }
            ownerCharacterId = newOwnerCharacterId;
            generation++;
            return generation;
        } finally {
            lock.unlock();
        }
    }

    public int ownerCharacterId() {
        int owner = ownerCharacterId;
        if (owner <= 0) {
            throw new IllegalStateException("Profile owner has not been initialized");
        }
        return owner;
    }

    public boolean isInitialized() {
        return ownerCharacterId > 0;
    }

    public long generation() {
        return generation;
    }

    public long markAttached() {
        lock.lock();
        try {
            generation++;
            return generation;
        } finally {
            lock.unlock();
        }
    }

    public long version() {
        return version.get();
    }

    public long markMutated() {
        return version.incrementAndGet();
    }
}
