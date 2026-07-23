package config;

import com.esotericsoftware.yamlbeans.YamlReader;
import constants.string.CharsetConstants;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Loads the Agent-owned deployment configuration independently of Cosmic server config. */
public class AgentYamlConfig {
    public static final String CONFIG_FILE_NAME = "agent-engine.yaml";
    public static final AgentYamlConfig config = loadConfig();

    public AgentEngineConfig agent;
    public Map<String, String> tuning;

    private static AgentYamlConfig loadConfig() {
        try {
            YamlReader reader = new YamlReader(
                    Files.newBufferedReader(Path.of(CONFIG_FILE_NAME), CharsetConstants.CHARSET));
            AgentYamlConfig loaded = reader.read(AgentYamlConfig.class);
            reader.close();
            if (loaded == null || loaded.agent == null || loaded.tuning == null) {
                throw new RuntimeException(
                        "Missing top-level 'agent' or 'tuning' configuration in "
                                + CONFIG_FILE_NAME);
            }
            AgentEngineConfigValidator.validate(loaded);
            return loaded;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(
                    "Could not read Agent config file " + CONFIG_FILE_NAME + ": " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not successfully parse Agent config file "
                            + CONFIG_FILE_NAME + ": " + e.getMessage());
        }
    }
}
