package server.partner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileTransitionLockManagerTest {
    @Test
    void lockOrderIsStableAndDeduplicated() {
        assertEquals(List.of(10, 20, 30),
                ProfileTransitionLockManager.stableOrder(List.of(30, 10, 20, 10)));
    }

    @Test
    void acquiredLocksReportCanonicalOrder() {
        ProfileTransitionLockManager manager = new ProfileTransitionLockManager();

        try (ProfileTransitionLockManager.LockHandle handle = manager.lockProfiles(List.of(9, 3))) {
            assertEquals(List.of(3, 9), handle.acquisitionOrder());
        }
    }
}
