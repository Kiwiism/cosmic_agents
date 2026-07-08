package server.agents.catalog;

public class CatalogLookupException extends RuntimeException {
    public CatalogLookupException(String message) {
        super(message);
    }

    public CatalogLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
