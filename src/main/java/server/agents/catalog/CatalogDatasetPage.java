package server.agents.catalog;

import java.util.List;

/** One bounded page from a catalog dataset; no mutable JSON tree escapes the catalog boundary. */
public record CatalogDatasetPage(
        CatalogDatasetDescriptor dataset,
        int offset,
        int limit,
        int total,
        List<CatalogRecord> records) {

    public CatalogDatasetPage {
        if (dataset == null || offset < 0 || limit < 1 || total < 0) {
            throw new IllegalArgumentException("valid catalog page metadata is required");
        }
        records = List.copyOf(records == null ? List.of() : records);
    }
}
