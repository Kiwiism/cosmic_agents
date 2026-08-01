package net.server.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class DatabaseConsoleBridgeSecurity {
    static final int MAX_REQUEST_BYTES = 64 * 1024;

    private DatabaseConsoleBridgeSecurity() {
    }

    static String requireStrongToken(String token) {
        if (token == null || token.isBlank() || token.length() < 32
                || "development-only-change-me".equals(token)) {
            throw new IllegalStateException("COSMIC_BRIDGE_TOKEN must contain at least 32 non-default characters");
        }
        return token;
    }

    static boolean matchesBearer(String header, String token) {
        if (header == null || !header.startsWith("Bearer ")) {
            return false;
        }
        byte[] supplied = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(supplied, expected);
    }

    static byte[] readBounded(InputStream input) throws IOException {
        byte[] body = input.readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body exceeds " + MAX_REQUEST_BYTES + " bytes");
        }
        return body;
    }
}
