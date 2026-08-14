package server.agents.economy.persistence;

import java.time.Instant;
import java.util.UUID;

public record CosmicOutboxRecord(UUID outboxId, String idempotencyKey, String operationKind,
                                 int primaryCharacterId, Integer secondaryCharacterId,
                                 String summary, Instant createdAt) { }
