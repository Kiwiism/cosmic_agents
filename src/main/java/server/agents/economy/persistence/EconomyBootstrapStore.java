package server.agents.economy.persistence;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface EconomyBootstrapStore {
    EconomyBootstrapStore NO_OP = (run, agent, at, config, catalog, snapshot) -> { };

    void recordImported(UUID runId, String agentId, Instant logicalAt, String configHash,
                        String catalogVersion, EconomyBootstrapSnapshot snapshot);
}
