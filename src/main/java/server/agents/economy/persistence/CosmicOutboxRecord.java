package server.agents.economy.persistence;

import java.time.Instant;
import java.util.UUID;

public record CosmicOutboxRecord(UUID outboxId, String idempotencyKey, String operationKind,
                                 int primaryCharacterId, Integer secondaryCharacterId,
                                 String summary, String payloadJson, UUID runId, Instant logicalAt,
                                 String decisionId, String activityId, String configRevision,
                                 String catalogRevision, String reasonCode,
                                 boolean primaryIsAgent, boolean secondaryIsAgent,
                                 Instant createdAt) { }
