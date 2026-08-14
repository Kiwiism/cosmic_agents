package server.agents.economy.scenario;

/** Validated configuration plus the immutable source evidence pinned to a run. */
public record LoadedEconomyConfig(
        EconomyEngineConfig config,
        String rawYaml,
        String sha256) {

    public LoadedEconomyConfig {
        if (config == null || rawYaml == null || rawYaml.isBlank()
                || sha256 == null || sha256.length() != 64) {
            throw new IllegalArgumentException("A loaded economy configuration requires source and hash");
        }
    }
}

