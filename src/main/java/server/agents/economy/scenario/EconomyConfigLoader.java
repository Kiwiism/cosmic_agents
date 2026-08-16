package server.agents.economy.scenario;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Reads a run configuration without coupling it to the global Agent YAML singleton. */
public final class EconomyConfigLoader {
    public static final Path DEFAULT_PATH = Path.of("economy-engine.yaml");
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public LoadedEconomyConfig load() {
        return load(DEFAULT_PATH);
    }

    public LoadedEconomyConfig load(Path path) {
        try {
            return load(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not read economy configuration " + path, failure);
        }
    }

    public LoadedEconomyConfig load(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new EconomyConfigException("Economy configuration cannot be blank");
        }
        try {
            YamlReader reader = new YamlReader(new StringReader(yaml));
            EconomyEngineConfig config = reader.read(EconomyEngineConfig.class);
            reader.close();
            EconomyConfigValidator.validate(config);
            return new LoadedEconomyConfig(config, yaml, JSON.writeValueAsString(config), sha256(yaml));
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not parse economy configuration", failure);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM does not provide SHA-256", impossible);
        }
    }
}
