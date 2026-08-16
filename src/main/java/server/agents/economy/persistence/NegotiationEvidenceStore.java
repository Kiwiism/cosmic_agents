package server.agents.economy.persistence;

import server.agents.economy.social.PublicNegotiationSession;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface NegotiationEvidenceStore {
    void record(UUID runId, int itemId, Instant openedAt, Instant closedAt,
                PublicNegotiationSession session, String settlementTransactionId);
}
