package server.agents.economy.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface EconomyParticipantBindingStore {
    EconomyParticipantBindingStore NO_OP = (runId, agentId, characterId, logicalAt) -> { };

    void bind(UUID runId, String agentId, int characterId, Instant logicalAt);

    default Map<String, Integer> load(UUID runId) { return Map.of(); }

    default void reserve(UUID runId, List<Reservation> reservations) {
        for (Reservation reservation : reservations)
            bind(runId, reservation.agentId(), reservation.characterId(), reservation.admittedAt());
    }

    record Reservation(String agentId, int characterId, Instant admittedAt) { }
}
