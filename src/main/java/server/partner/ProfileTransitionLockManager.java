package server.partner;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Acquires profile locks in canonical character-ID order. */
public final class ProfileTransitionLockManager {
    private final ConcurrentHashMap<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();

    public LockHandle lockProfiles(Collection<Integer> profileOwnerIds) {
        List<Integer> order = stableOrder(profileOwnerIds);
        for (Integer profileOwnerId : order) {
            locks.computeIfAbsent(profileOwnerId, ignored -> new ReentrantLock(true)).lock();
        }
        return new LockHandle(order);
    }

    public static List<Integer> stableOrder(Collection<Integer> profileOwnerIds) {
        if (profileOwnerIds == null || profileOwnerIds.isEmpty()) {
            throw new IllegalArgumentException("Profile lock set cannot be empty");
        }
        List<Integer> order = profileOwnerIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
        if (order.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Profile owner IDs must be positive");
        }
        return order;
    }

    public final class LockHandle implements AutoCloseable {
        private final List<Integer> order;
        private boolean closed;

        private LockHandle(List<Integer> order) {
            this.order = order;
        }

        public List<Integer> acquisitionOrder() {
            return order;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (int index = order.size() - 1; index >= 0; index--) {
                locks.get(order.get(index)).unlock();
            }
        }
    }
}
