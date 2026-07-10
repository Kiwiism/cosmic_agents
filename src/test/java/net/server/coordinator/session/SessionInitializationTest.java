package net.server.coordinator.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionInitializationTest {
    @Test
    void membershipIsAtomicAndCanBeFinalized() {
        SessionInitialization initialization = new SessionInitialization();

        assertEquals(InitializationResult.SUCCESS, initialization.initialize("127.0.0.1"));
        assertEquals(InitializationResult.ALREADY_INITIALIZED, initialization.initialize("127.0.0.1"));

        initialization.finalize("127.0.0.1");
        assertEquals(InitializationResult.SUCCESS, initialization.initialize("127.0.0.1"));
    }

    @Test
    void rejectsMissingHostKeys() {
        SessionInitialization initialization = new SessionInitialization();

        assertEquals(InitializationResult.ERROR, initialization.initialize(null));
        assertEquals(InitializationResult.ERROR, initialization.initialize("  "));
    }
}
