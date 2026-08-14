package server.agents.economy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EconomicEventStore {
    /** @return true when appended, false when the idempotency key already exists. */
    boolean append(EconomicEvent event);

    List<EconomicEvent> read(UUID runId, Instant afterExclusive, int limit);
}
