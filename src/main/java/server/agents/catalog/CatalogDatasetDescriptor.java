package server.agents.catalog;

import java.util.List;

/** Immutable metadata for one loaded catalog dataset. */
public record CatalogDatasetDescriptor(
        String key,
        String group,
        String fileName,
        String sourcePath,
        boolean required,
        int recordCount,
        String recordField,
        List<String> topLevelFields) {

    public CatalogDatasetDescriptor {
        key = requiredText(key, "dataset key");
        group = requiredText(group, "dataset group");
        fileName = requiredText(fileName, "dataset file name");
        sourcePath = requiredText(sourcePath, "dataset source path");
        recordField = requiredText(recordField, "record field");
        topLevelFields = List.copyOf(topLevelFields == null ? List.of() : topLevelFields);
        if (recordCount < 0) throw new IllegalArgumentException("record count cannot be negative");
    }

    private static String requiredText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
