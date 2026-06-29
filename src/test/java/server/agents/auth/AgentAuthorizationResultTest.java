package server.agents.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentAuthorizationResultTest {
    @Test
    void allowedResultPreservesAutoRegistrationFlag() {
        AgentAuthorizationResult result = AgentAuthorizationResult.allowed(true);

        assertTrue(result.allowed());
        assertTrue(result.autoRegistered());
        assertNull(result.failureMessage());
    }

    @Test
    void deniedResultPreservesFailureMessage() {
        AgentAuthorizationResult result = AgentAuthorizationResult.denied("nope");

        assertFalse(result.allowed());
        assertFalse(result.autoRegistered());
        assertEquals("nope", result.failureMessage());
    }
}
