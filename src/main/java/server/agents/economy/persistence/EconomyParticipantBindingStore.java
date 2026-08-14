package server.agents.economy.persistence;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface EconomyParticipantBindingStore {
    EconomyParticipantBindingStore NO_OP = (runId, agentId, characterId, logicalAt) -> { };

    void bind(UUID runId, String agentId, int characterId, Instant logicalAt);
}
