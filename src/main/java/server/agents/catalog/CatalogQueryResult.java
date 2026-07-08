package server.agents.catalog;

import java.util.Optional;

public record CatalogQueryResult<T>(boolean found, T value, String reason) {
    public static <T> CatalogQueryResult<T> found(T value) {
        return new CatalogQueryResult<>(true, value, "");
    }

    public static <T> CatalogQueryResult<T> notFound(String reason) {
        return new CatalogQueryResult<>(false, null, reason);
    }

    public Optional<T> optional() {
        return found ? Optional.ofNullable(value) : Optional.empty();
    }
}
