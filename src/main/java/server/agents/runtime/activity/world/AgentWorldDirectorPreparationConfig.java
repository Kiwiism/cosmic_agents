package server.agents.runtime.activity.world;

/** Explicit preparation gates. No live-control setting exists in this rollout. */
public record AgentWorldDirectorPreparationConfig(
        boolean commandDrivenShadowEnabled,
        boolean automaticShadowSamplingEnabled,
        boolean liveControlEnabled) {

    public AgentWorldDirectorPreparationConfig {
        if (automaticShadowSamplingEnabled || liveControlEnabled) {
            throw new IllegalArgumentException(
                    "preparation permits only command-driven, non-owning shadow observation");
        }
    }

    public static AgentWorldDirectorPreparationConfig defaults() {
        return new AgentWorldDirectorPreparationConfig(true, false, false);
    }
}
