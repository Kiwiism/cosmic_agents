package server.agents.economy.communication;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stable boundary usable inside or outside a physical FM visit. */
public interface EconomyCommunicationPort {
    EconomicIntent publish(String actorAgentId, String counterpartyAgentId,
                           EconomicIntent.Kind kind, int itemId, String itemFingerprint,
                           int quantity, long mesos, Integer preferredMapId,
                           String publicText, Map<String, Object> attributes,
                           Instant createdAt, Duration lifetime);

    List<EconomicIntent> discover(String requestingAgentId, int itemId, Instant asOf, int limit);

    boolean resolve(String requestingAgentId, java.util.UUID intentId, EconomicIntent.Status status,
                    Instant resolvedAt, String reason);

    static EconomyCommunicationPort disabled() {
        return new EconomyCommunicationPort() {
            @Override public EconomicIntent publish(String actor, String counterparty,
                    EconomicIntent.Kind kind, int itemId, String fingerprint, int quantity, long mesos,
                    Integer mapId, String text, Map<String, Object> attributes, Instant at, Duration lifetime) {
                throw new IllegalStateException("implicit economic intents are disabled");
            }
            @Override public List<EconomicIntent> discover(String agentId, int itemId, Instant at, int limit) {
                return List.of();
            }
            @Override public boolean resolve(String agentId, java.util.UUID id,
                                             EconomicIntent.Status status,
                                             Instant at, String reason) { return false; }
        };
    }
}
