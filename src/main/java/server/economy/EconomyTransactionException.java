package server.economy;

public final class EconomyTransactionException extends RuntimeException {
    public EconomyTransactionException(String message) {
        super(message);
    }

    public EconomyTransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
