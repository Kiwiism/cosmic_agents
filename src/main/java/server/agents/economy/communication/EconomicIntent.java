package server.agents.economy.communication;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Structured agent-to-agent economic communication; rendered chat is never authoritative input. */
public record EconomicIntent(UUID intentId, UUID runId, String actorAgentId,
                             String counterpartyAgentId, Kind kind, int itemId,
                             String itemFingerprint, int quantity, long mesos,
                             Integer preferredMapId, String publicText,
                             Map<String, Object> attributes, Instant createdAt,
                             Instant expiresAt, Status status) {
    public enum Kind { BUY_INTEREST, SELL_INTEREST, MESO_OFFER, COUNTER_OFFER, ACCEPT, REJECT }
    public enum Status { OPEN, ACCEPTED, REJECTED, EXPIRED, CANCELLED, SETTLED }

    public EconomicIntent {
        if (intentId == null || runId == null || actorAgentId == null || actorAgentId.isBlank()
                || kind == null || itemId <= 0 || quantity <= 0 || mesos < 0
                || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)
                || status == null) throw new IllegalArgumentException("invalid economic intent");
        counterpartyAgentId = counterpartyAgentId == null ? "" : counterpartyAgentId;
        itemFingerprint = itemFingerprint == null ? "" : itemFingerprint;
        publicText = publicText == null ? "" : publicText;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (counterpartyAgentId.equals(actorAgentId))
            throw new IllegalArgumentException("self-directed economic intent is invalid");
        if ((kind == Kind.MESO_OFFER || kind == Kind.COUNTER_OFFER) && mesos <= 0)
            throw new IllegalArgumentException("numeric offers require positive mesos");
    }
}
