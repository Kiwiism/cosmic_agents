package server.agents.integration;

import client.Character;
import server.agents.economy.communication.EconomicIntent;
import server.agents.economy.market.AgentItemValuationService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import server.agents.economy.session.EconomySessionPort;
import server.agents.economy.ownership.InventoryReview;
import server.agents.economy.ownership.LegacyDispositionProposal;

/** Stable optional agent-facing access to economy knowledge and structured communication. */
public final class AgentEconomyRuntime {
    private static volatile Gateway gateway = Gateway.disabled();
    private AgentEconomyRuntime() { }

    public static void install(Gateway value) { gateway = Objects.requireNonNull(value); }
    public static void clear() { gateway = Gateway.disabled(); }
    public static boolean available() { return gateway.available(); }

    /** Installed per-Agent Commerce admission port; absent outside a managed economy run. */
    public static Optional<EconomySessionPort> sessionPort() { return gateway.sessionPort(); }

    public static AgentItemValuationService.Valuation valueItem(String agentId, int itemId, Instant at) {
        return gateway.valueItem(agentId, itemId, at);
    }

    public static EconomicIntent publishIntent(String actorAgentId, String counterpartyAgentId,
                                               EconomicIntent.Kind kind, int itemId,
                                               String fingerprint, int quantity, long mesos,
                                               Integer preferredMapId, String publicText,
                                               Map<String, Object> attributes, Instant at,
                                               Duration lifetime) {
        return gateway.publishIntent(actorAgentId, counterpartyAgentId, kind, itemId, fingerprint,
                quantity, mesos, preferredMapId, publicText, attributes, at, lifetime);
    }

    public static List<EconomicIntent> discoverIntents(String agentId, int itemId, Instant at, int limit) {
        return gateway.discoverIntents(agentId, itemId, at, limit);
    }

    public static boolean resolveIntent(String agentId, java.util.UUID intentId,
                                        EconomicIntent.Status status, Instant at, String reason) {
        return gateway.resolveIntent(agentId, intentId, status, at, reason);
    }

    public static InventoryReview reviewInventory(Character agent, String agentId,
                                                  List<LegacyDispositionProposal> proposals,
                                                  Instant at) {
        return gateway.reviewInventory(agent, agentId, proposals, at);
    }

    public interface Gateway {
        boolean available();
        default Optional<EconomySessionPort> sessionPort() { return Optional.empty(); }
        AgentItemValuationService.Valuation valueItem(String agentId, int itemId, Instant at);
        EconomicIntent publishIntent(String actorAgentId, String counterpartyAgentId,
                                     EconomicIntent.Kind kind, int itemId, String fingerprint,
                                     int quantity, long mesos, Integer preferredMapId,
                                     String publicText, Map<String, Object> attributes,
                                     Instant at, Duration lifetime);
        List<EconomicIntent> discoverIntents(String agentId, int itemId, Instant at, int limit);
        boolean resolveIntent(String agentId, java.util.UUID intentId, EconomicIntent.Status status,
                              Instant at, String reason);
        InventoryReview reviewInventory(Character agent, String agentId,
                                        List<LegacyDispositionProposal> proposals, Instant at);

        static Gateway disabled() {
            return new Gateway() {
                @Override public boolean available() { return false; }
                @Override public AgentItemValuationService.Valuation valueItem(
                        String agentId, int itemId, Instant at) {
                    return AgentItemValuationService.unknown().value(agentId, itemId, at);
                }
                @Override public EconomicIntent publishIntent(String actor, String counterparty,
                        EconomicIntent.Kind kind, int itemId, String fingerprint, int quantity, long mesos,
                        Integer mapId, String text, Map<String, Object> attributes, Instant at,
                        Duration lifetime) {
                    throw new IllegalStateException("economy runtime is not available");
                }
                @Override public List<EconomicIntent> discoverIntents(
                        String agentId, int itemId, Instant at, int limit) { return List.of(); }
                @Override public boolean resolveIntent(String agentId, java.util.UUID intentId,
                                                       EconomicIntent.Status status, Instant at,
                                                       String reason) { return false; }
                @Override public InventoryReview reviewInventory(Character agent, String agentId,
                        List<LegacyDispositionProposal> proposals, Instant at) {
                    throw new IllegalStateException("economy runtime is not available");
                }
            };
        }
    }
}
