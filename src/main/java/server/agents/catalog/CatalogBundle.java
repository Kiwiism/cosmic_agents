package server.agents.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class CatalogBundle {
    private final CatalogLoadOptions options;
    private final ObjectMapper mapper;
    private final Map<CatalogFile, JsonNode> files;
    private final Map<CatalogFile, Path> loadedPaths;
    private final CatalogIndexes indexes;

    CatalogBundle(CatalogLoadOptions options,
                  ObjectMapper mapper,
                  Map<CatalogFile, JsonNode> files,
                  Map<CatalogFile, Path> loadedPaths) {
        this.options = options;
        this.mapper = mapper;
        this.files = Map.copyOf(new EnumMap<>(files));
        this.loadedPaths = Map.copyOf(new EnumMap<>(loadedPaths));
        this.indexes = CatalogIndexes.build(this, mapper);
    }

    public CatalogLoadOptions options() {
        return options;
    }

    public boolean hasFile(String key) {
        return files.keySet().stream().anyMatch(file -> file.key().equals(key));
    }

    public Optional<Path> loadedPath(String key) {
        return loadedPaths.entrySet().stream()
                .filter(entry -> entry.getKey().key().equals(key))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    CatalogIndexes indexes() {
        return indexes;
    }

    JsonNode node(CatalogFile file) {
        return files.get(file);
    }

    ObjectMapper mapper() {
        return mapper;
    }
}
