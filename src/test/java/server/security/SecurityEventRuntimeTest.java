package server.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventRuntimeTest {
    @AfterEach
    void clear() {
        SecurityEventRuntime.clearForTesting();
    }

    @Test
    void retainsStructuredEvidenceWithoutRawRemoteAddress() {
        SecurityEvent event = SecurityEventRuntime.recordExternal(SecurityEventType.MALFORMED_PACKET,
                SecuritySeverity.WARNING, "203.0.113.42", Map.of("opcode", "123"));

        assertEquals("123", event.evidence().get("opcode"));
        assertNotEquals("203.0.113.42", event.remoteFingerprint());
        assertTrue(SecurityEventRuntime.snapshot().contains(event));
    }
}
