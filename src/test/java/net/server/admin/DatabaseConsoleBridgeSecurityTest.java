package net.server.admin;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConsoleBridgeSecurityTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @Test
    void requiresStrongNonDefaultToken() {
        assertThrows(IllegalStateException.class, () -> DatabaseConsoleBridgeSecurity.requireStrongToken(null));
        assertThrows(IllegalStateException.class,
                () -> DatabaseConsoleBridgeSecurity.requireStrongToken("development-only-change-me"));
        assertTrue(DatabaseConsoleBridgeSecurity.requireStrongToken(TOKEN).equals(TOKEN));
    }

    @Test
    void bearerComparisonRejectsMalformedOrWrongValues() {
        assertTrue(DatabaseConsoleBridgeSecurity.matchesBearer("Bearer " + TOKEN, TOKEN));
        assertFalse(DatabaseConsoleBridgeSecurity.matchesBearer(TOKEN, TOKEN));
        assertFalse(DatabaseConsoleBridgeSecurity.matchesBearer("Bearer wrong", TOKEN));
    }

    @Test
    void requestBodiesAreBounded() throws Exception {
        byte[] acceptable = new byte[DatabaseConsoleBridgeSecurity.MAX_REQUEST_BYTES];
        assertArrayEquals(acceptable,
                DatabaseConsoleBridgeSecurity.readBounded(new ByteArrayInputStream(acceptable)));
        byte[] oversized = new byte[DatabaseConsoleBridgeSecurity.MAX_REQUEST_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConsoleBridgeSecurity.readBounded(new ByteArrayInputStream(oversized)));
    }
}
