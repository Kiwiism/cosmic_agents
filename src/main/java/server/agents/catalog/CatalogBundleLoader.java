package server.agents.catalog;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class CatalogBundleLoader {
    private final CatalogJsonReader reader;

    public CatalogBundleLoader() {
        this.reader = new CatalogJsonReader();
    }

    public CatalogBundle load(CatalogLoadOptions options) {
        Map<CatalogFile, JsonNode> files = new EnumMap<>(CatalogFile.class);
        Map<CatalogFile, Path> paths = new EnumMap<>(CatalogFile.class);

        for (CatalogFile file : CatalogFile.values()) {
            Path path = file.resolve(options).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                if (file.required()) {
                    throw new CatalogLookupException("Missing required catalog file '" + file.key() + "': " + path);
                }
                continue;
            }
            files.put(file, reader.read(path));
            paths.put(file, path);
        }

        return new CatalogBundle(options, reader.mapper(), files, paths);
    }
}
