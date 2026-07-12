package server.partner;

public final class PartnerPersistenceException extends RuntimeException {
    public PartnerPersistenceException(String message) {
        super(message);
    }

    public PartnerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
