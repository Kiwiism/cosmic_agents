package server.security;

final class SecurityEventStoreException extends RuntimeException {
    SecurityEventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
