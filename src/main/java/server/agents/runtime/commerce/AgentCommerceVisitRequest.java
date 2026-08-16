package server.agents.runtime.commerce;

import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.session.EconomySessionPort;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** External request for one bounded per-Agent Commerce visit. */
public record AgentCommerceVisitRequest(
        String requestId,
        String callerId,
        CommerceParticipant participant,
        Purpose purpose,
        long maximumDurationMs,
        long maximumIdleMs,
        Map<String, String> attributes) {
    public AgentCommerceVisitRequest {
        requestId = normalize(requestId, "request id");
        callerId = normalize(callerId, "caller id");
        if (participant == null || purpose == null || maximumDurationMs <= 0L
                || maximumIdleMs < 0L || maximumIdleMs > maximumDurationMs) {
            throw new IllegalArgumentException("invalid Commerce visit request");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public EconomySessionPort.EntryRequest entryRequest() {
        UUID id = UUID.nameUUIDFromBytes(requestId.getBytes(StandardCharsets.UTF_8));
        return new EconomySessionPort.EntryRequest(id, purpose.name(),
                Duration.ofMillis(maximumDurationMs), Duration.ofMillis(maximumIdleMs),
                attributes);
    }

    public enum Purpose {
        SELL_INVENTORY,
        BUY_SUPPLIES,
        UPGRADE_EQUIPMENT,
        FULFIL_INTENT,
        PERIODIC_MARKET_VISIT,
        OBSERVATION
    }

    private static String normalize(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }
}
