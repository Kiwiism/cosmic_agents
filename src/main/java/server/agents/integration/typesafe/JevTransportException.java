package server.agents.integration.typesafe;

/** A failed System One round trip. {@link #retryable()} follows the API's own guidance. */
public final class JevTransportException extends Exception {
    private final int statusCode;
    private final boolean retryable;

    public JevTransportException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public JevTransportException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.statusCode = 0;
        this.retryable = retryable;
    }

    /** HTTP status, or {@code 0} when the failure happened before a response arrived. */
    public int statusCode() {
        return statusCode;
    }

    /** True for 429, 529, 5xx and connection failures; false for 401/403/422 and parse errors. */
    public boolean retryable() {
        return retryable;
    }
}
