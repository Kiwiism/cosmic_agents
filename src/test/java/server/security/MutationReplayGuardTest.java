package server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationReplayGuardTest {
    @AfterEach
    void clear() {
        MutationReplayGuard.clearForTesting();
    }

    @Test
    void rejectsOnlyTheSameCharacterFamilyAndResourceWithinTheWindow() {
        assertTrue(MutationReplayGuard.acquire(10, "DUEY_CLAIM", 42));
        assertFalse(MutationReplayGuard.acquire(10, "DUEY_CLAIM", 42));
        assertTrue(MutationReplayGuard.acquire(10, "DUEY_CLAIM", 43));
        assertTrue(MutationReplayGuard.acquire(11, "DUEY_CLAIM", 42));
    }
}
