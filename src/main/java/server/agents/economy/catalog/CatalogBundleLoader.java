package server.agents.economy.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.scenario.EconomyConfigException;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hashes every configured authority input and rejects mixed generated revisions. */
public final class CatalogBundleLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public CatalogBundleDescriptor load(EconomyEngineConfig.Catalog config) {
        if (config == null) throw new EconomyConfigException("catalog configuration is required");
        Map<String, String> hashes = new LinkedHashMap<>();
        String adaptiveRevision = null;
        MessageDigest bundle = digest();
        for (String resource : config.adaptiveResources) {
            byte[] bytes = read(resource);
            hashes.put(resource, sha256(bytes));
            bundle.update(bytes);
            String revision = revision(resource, bytes);
            if (adaptiveRevision == null) adaptiveRevision = revision;
            else if (config.requireMatchingAdaptiveRevision && !adaptiveRevision.equals(revision)) {
                throw new EconomyConfigException("Catalog mixes adaptive revisions at " + resource);
            }
        }
        for (String resource : config.sqlResources) {
            byte[] bytes = read(resource);
            hashes.put(resource, sha256(bytes));
            bundle.update(bytes);
        }
        for (String resource : config.mechanicalResources) {
            byte[] bytes = read(resource);
            hashes.put(resource, sha256(bytes));
            bundle.update(bytes);
        }
        if (adaptiveRevision == null) throw new EconomyConfigException("No adaptive revision found");
        return new CatalogBundleDescriptor(config.bundleId, adaptiveRevision,
                HexFormat.of().formatHex(bundle.digest()), hashes);
    }

    private static byte[] read(String resource) {
        try (InputStream input = CatalogBundleLoader.class.getResourceAsStream(resource)) {
            if (input == null) throw new EconomyConfigException("Missing catalog resource " + resource);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not read catalog resource " + resource, failure);
        }
    }

    private static String revision(String resource, byte[] bytes) {
        try {
            JsonNode root = JSON.readTree(bytes);
            if (root.path("schemaVersion").asInt() <= 0 || root.path("entries").isEmpty()) {
                throw new EconomyConfigException("Catalog is empty or unversioned: " + resource);
            }
            String revision = root.path("revision").asText();
            if (revision.isBlank()) throw new EconomyConfigException("Catalog revision missing: " + resource);
            return revision;
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not parse catalog resource " + resource, failure);
        }
    }

    private static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }
}
