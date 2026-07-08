package server.agents.catalog;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class CatalogJsonReader {
    private final ObjectMapper mapper;

    CatalogJsonReader() {
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    ObjectMapper mapper() {
        return mapper;
    }

    JsonNode read(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return mapper.readTree(input);
        } catch (IOException e) {
            throw new CatalogLookupException("Failed to read catalog JSON: " + path, e);
        }
    }
}
