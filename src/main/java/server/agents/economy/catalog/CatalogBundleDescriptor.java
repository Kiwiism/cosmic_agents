package server.agents.economy.catalog;

import java.util.Map;

public record CatalogBundleDescriptor(String bundleId, String adaptiveRevision, String version,
                                      Map<String, String> resourceHashes) {
    public CatalogBundleDescriptor {
        if (bundleId == null || bundleId.isBlank() || adaptiveRevision == null
                || adaptiveRevision.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Catalog descriptor identifiers are required");
        }
        resourceHashes = Map.copyOf(resourceHashes);
    }
}
