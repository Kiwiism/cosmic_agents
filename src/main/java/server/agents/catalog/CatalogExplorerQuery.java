package server.agents.catalog;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Generic read-only catalog explorer used by presentation layers.
 * Gameplay policy should continue to use the typed queries on {@link CatalogQueryService}.
 */
public final class CatalogExplorerQuery {
    private final CatalogBundle bundle;

    CatalogExplorerQuery(CatalogBundle bundle) {
        this.bundle = bundle;
    }

    public List<CatalogDatasetDescriptor> datasets() {
        return bundle.loadedFiles().stream()
                .map(this::descriptor)
                .sorted(Comparator.comparing(CatalogDatasetDescriptor::group)
                        .thenComparing(CatalogDatasetDescriptor::key))
                .toList();
    }

    public Optional<CatalogDatasetDescriptor> dataset(String key) {
        CatalogFile file = file(key);
        return file == null ? Optional.empty() : Optional.of(descriptor(file));
    }

    public Optional<CatalogRecord> root(String key) {
        CatalogFile file = file(key);
        if (file == null) return Optional.empty();
        JsonNode root = bundle.node(file);
        return root != null && root.isObject()
                ? Optional.of(CatalogRecord.from(root, bundle.mapper())) : Optional.empty();
    }

    public CatalogDatasetPage page(String key, int offset, int limit) {
        return search(key, "", offset, limit);
    }

    public CatalogDatasetPage search(String key, String query, int offset, int limit) {
        CatalogFile file = requireFile(key);
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("catalog page requires offset >= 0 and limit 1..200");
        }
        CatalogDatasetDescriptor descriptor = descriptor(file);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<CatalogRecord> matches = records(file).stream()
                .filter(record -> normalized.isEmpty()
                        || record.fields().toString().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        int from = Math.min(offset, matches.size());
        int to = Math.min(matches.size(), from + limit);
        return new CatalogDatasetPage(
                descriptor, offset, limit, matches.size(), matches.subList(from, to));
    }

    public Optional<CatalogRecord> find(
            String key, String identityField, String identityValue) {
        if (identityField == null || identityField.isBlank()
                || identityValue == null || identityValue.isBlank()) {
            throw new IllegalArgumentException("identity field and value are required");
        }
        CatalogFile file = requireFile(key);
        String expected = identityValue.trim();
        return records(file).stream()
                .filter(record -> record.stringValue(identityField).orElse("").equals(expected))
                .findFirst();
    }

    private CatalogDatasetDescriptor descriptor(CatalogFile file) {
        JsonNode root = bundle.node(file);
        Collection collection = collection(root);
        Path path = bundle.loadedPath(file.key()).orElseThrow();
        List<String> topFields = new ArrayList<>();
        if (root != null && root.isObject()) root.fieldNames().forEachRemaining(topFields::add);
        topFields.sort(String::compareTo);
        return new CatalogDatasetDescriptor(
                file.key(), file.group(), file.fileName(), path.toString(), file.required(),
                collection.node().size(), collection.field(), topFields);
    }

    private List<CatalogRecord> records(CatalogFile file) {
        JsonNode records = collection(bundle.node(file)).node();
        if (!records.isArray()) return List.of();
        List<CatalogRecord> result = new ArrayList<>();
        for (JsonNode record : records) {
            if (record.isObject()) result.add(CatalogRecord.from(record, bundle.mapper()));
        }
        return List.copyOf(result);
    }

    private static Collection collection(JsonNode root) {
        if (root == null) return new Collection("$",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode());
        if (root.isArray()) return new Collection("$", root);
        if (!root.isObject()) return new Collection("$", root);
        String selected = "@root";
        JsonNode selectedNode = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        var fields = root.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getValue().isArray() && entry.getValue().size() > selectedNode.size()) {
                selected = entry.getKey();
                selectedNode = entry.getValue();
            }
        }
        return new Collection(selected, selectedNode);
    }

    private CatalogFile file(String key) {
        if (key == null || key.isBlank()) return null;
        return bundle.loadedFiles().stream()
                .filter(candidate -> candidate.key().equals(key.trim()))
                .findFirst().orElse(null);
    }

    private CatalogFile requireFile(String key) {
        CatalogFile file = file(key);
        if (file == null) throw new CatalogLookupException("Unknown loaded catalog dataset: " + key);
        return file;
    }

    private record Collection(String field, JsonNode node) { }
}
