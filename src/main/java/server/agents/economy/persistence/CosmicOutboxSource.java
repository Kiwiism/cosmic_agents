package server.agents.economy.persistence;

import java.util.List;
import java.util.UUID;

public interface CosmicOutboxSource {
    List<CosmicOutboxRecord> pending(int limit);
    void markPublished(UUID outboxId);
    void markFailed(UUID outboxId, String error);
}
