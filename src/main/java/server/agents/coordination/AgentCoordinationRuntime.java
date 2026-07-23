package server.agents.coordination;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/** Bounded in-process router for structured cohort, party, and direct-Agent messages. */
public final class AgentCoordinationRuntime {
    private static final int DEFAULT_ROUTE_CAPACITY = config.AgentTuning.intValue("server.agents.coordination.AgentCoordinationRuntime.DEFAULT_ROUTE_CAPACITY");
    private static final int DEFAULT_MAX_ROUTES = config.AgentTuning.intValue("server.agents.coordination.AgentCoordinationRuntime.DEFAULT_MAX_ROUTES");
    private static final int DEFAULT_MAX_RECEIPTS = config.AgentTuning.intValue("server.agents.coordination.AgentCoordinationRuntime.DEFAULT_MAX_RECEIPTS");
    private static final long DEFAULT_TTL_MS = config.AgentTuning.longValue("server.agents.coordination.AgentCoordinationRuntime.DEFAULT_TTL_MS");

    private static final CopyOnWriteArrayList<Consumer<AgentCoordinationMessage>> legacyListeners =
            new CopyOnWriteArrayList<>();
    private static final Map<RouteKey, AgentCoordinationInbox> inboxes = new ConcurrentHashMap<>();
    private static final Map<RouteKey, CopyOnWriteArrayList<Consumer<AgentCoordinationEnvelope>>> routeListeners =
            new ConcurrentHashMap<>();
    private static final Map<ReceiptKey, AgentCoordinationReceipt> receipts = new ConcurrentHashMap<>();
    private static final AtomicLong nextMessageId = new AtomicLong();
    private static final LongAdder published = new LongAdder();
    private static final LongAdder accepted = new LongAdder();
    private static final LongAdder rejectedCapacity = new LongAdder();
    private static final LongAdder expired = new LongAdder();
    private static final LongAdder delivered = new LongAdder();
    private static final LongAdder listenerFailures = new LongAdder();
    private static final LongAdder receiptCount = new LongAdder();

    private AgentCoordinationRuntime() {
    }

    /** Compatibility observer for callers that have not migrated to route-specific delivery. */
    public static AutoCloseable subscribe(Consumer<AgentCoordinationMessage> listener) {
        Consumer<AgentCoordinationMessage> required = Objects.requireNonNull(listener);
        legacyListeners.add(required);
        return () -> legacyListeners.remove(required);
    }

    public static AutoCloseable subscribe(AgentCoordinationScope scope,
                                          long routeId,
                                          Consumer<AgentCoordinationEnvelope> listener) {
        RouteKey key = new RouteKey(scope, routeId);
        Consumer<AgentCoordinationEnvelope> required = Objects.requireNonNull(listener);
        CopyOnWriteArrayList<Consumer<AgentCoordinationEnvelope>> listeners =
                routeListeners.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        listeners.add(required);
        return () -> {
            listeners.remove(required);
            if (listeners.isEmpty()) {
                routeListeners.remove(key, listeners);
            }
        };
    }

    /** Compatibility cohort publication with a bounded default lifetime. */
    public static void publish(AgentCoordinationMessage message) {
        Objects.requireNonNull(message);
        long routeId = message.cohortId() > 0 ? message.cohortId() : message.sourceAgentCharacterId();
        publishRouted(message, AgentCoordinationScope.COHORT, routeId, 0,
                DEFAULT_TTL_MS, false, "");
    }

    public static AgentCoordinationPublishResult publishRouted(
            AgentCoordinationMessage message,
            AgentCoordinationScope scope,
            long routeId,
            int targetAgentCharacterId,
            long ttlMs,
            boolean acknowledgementRequired,
            String correlationId) {
        Objects.requireNonNull(message);
        long now = System.currentTimeMillis();
        long messageId = nextMessageId.incrementAndGet();
        long boundedTtl = Math.max(1L, ttlMs);
        long expiresAt = saturatedAdd(now, boundedTtl);
        String correlation = correlationId == null || correlationId.isBlank()
                ? "coordination:" + messageId : correlationId;
        AgentCoordinationEnvelope envelope = new AgentCoordinationEnvelope(
                messageId, correlation, scope, routeId, targetAgentCharacterId,
                now, expiresAt, acknowledgementRequired, message);
        return publish(envelope, System.currentTimeMillis());
    }

    static AgentCoordinationPublishResult publish(AgentCoordinationEnvelope envelope, long nowMillis) {
        Objects.requireNonNull(envelope);
        published.increment();
        if (envelope.expired(nowMillis)) {
            expired.increment();
            return new AgentCoordinationPublishResult(false, envelope, "expired");
        }
        RouteKey key = new RouteKey(envelope.scope(), envelope.routeId());
        AgentCoordinationInbox inbox = inboxes.get(key);
        if (inbox == null) {
            if (inboxes.size() >= configuredMaxRoutes()) {
                rejectedCapacity.increment();
                return new AgentCoordinationPublishResult(false, envelope, "route-capacity");
            }
            inbox = inboxes.computeIfAbsent(key,
                    ignored -> new AgentCoordinationInbox(configuredRouteCapacity()));
        }
        AgentCoordinationInbox.OfferResult offer = inbox.offer(envelope, nowMillis);
        expired.add(offer.expired());
        if (!offer.accepted()) {
            rejectedCapacity.increment();
            return new AgentCoordinationPublishResult(false, envelope, "inbox-capacity");
        }

        accepted.increment();
        notifyRouteListeners(key, envelope);
        notifyLegacyListeners(envelope.message());
        return new AgentCoordinationPublishResult(true, envelope, "");
    }

    public static List<AgentCoordinationEnvelope> drain(AgentCoordinationScope scope,
                                                        long routeId,
                                                        int limit,
                                                        long nowMillis) {
        AgentCoordinationInbox inbox = inboxes.get(new RouteKey(scope, routeId));
        if (inbox == null || limit <= 0) {
            return List.of();
        }
        AgentCoordinationInbox.DrainResult result = inbox.drain(limit, nowMillis);
        expired.add(result.expired());
        delivered.add(result.envelopes().size());
        return result.envelopes();
    }

    public static AgentCoordinationReceipt recordDisposition(long messageId,
                                                              int agentCharacterId,
                                                              AgentCoordinationDisposition disposition,
                                                              long nowMillis,
                                                              String detail) {
        AgentCoordinationReceipt receipt = new AgentCoordinationReceipt(
                messageId, agentCharacterId, disposition, nowMillis, detail);
        if (receipts.size() >= configuredMaxReceipts()) {
            ReceiptKey oldest = receipts.keySet().stream().findFirst().orElse(null);
            if (oldest != null) {
                receipts.remove(oldest);
            }
        }
        receipts.put(new ReceiptKey(messageId, agentCharacterId), receipt);
        receiptCount.increment();
        return receipt;
    }

    public static AgentCoordinationReceipt receipt(long messageId, int agentCharacterId) {
        return receipts.get(new ReceiptKey(messageId, agentCharacterId));
    }

    public static AgentCoordinationRuntimeSnapshot snapshot() {
        int queued = inboxes.values().stream().mapToInt(AgentCoordinationInbox::size).sum();
        return new AgentCoordinationRuntimeSnapshot(
                published.sum(), accepted.sum(), rejectedCapacity.sum(), expired.sum(),
                delivered.sum(), listenerFailures.sum(), receiptCount.sum(), inboxes.size(), queued);
    }

    static void resetForTests() {
        legacyListeners.clear();
        inboxes.clear();
        routeListeners.clear();
        receipts.clear();
        nextMessageId.set(0L);
        published.reset();
        accepted.reset();
        rejectedCapacity.reset();
        expired.reset();
        delivered.reset();
        listenerFailures.reset();
        receiptCount.reset();
    }

    private static void notifyRouteListeners(RouteKey key, AgentCoordinationEnvelope envelope) {
        for (Consumer<AgentCoordinationEnvelope> listener
                : routeListeners.getOrDefault(key, new CopyOnWriteArrayList<>())) {
            try {
                listener.accept(envelope);
            } catch (Throwable ignored) {
                listenerFailures.increment();
            }
        }
    }

    private static void notifyLegacyListeners(AgentCoordinationMessage message) {
        for (Consumer<AgentCoordinationMessage> listener : legacyListeners) {
            try {
                listener.accept(message);
            } catch (Throwable ignored) {
                listenerFailures.increment();
            }
        }
    }

    private static int configuredRouteCapacity() {
        return positiveIntegerProperty("agents.coordination.routeCapacity", DEFAULT_ROUTE_CAPACITY);
    }

    private static int configuredMaxRoutes() {
        return positiveIntegerProperty("agents.coordination.maxRoutes", DEFAULT_MAX_ROUTES);
    }

    private static int configuredMaxReceipts() {
        return positiveIntegerProperty("agents.coordination.maxReceipts", DEFAULT_MAX_RECEIPTS);
    }

    private static int positiveIntegerProperty(String key, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty(key, String.valueOf(fallback))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long saturatedAdd(long value, long increment) {
        return Long.MAX_VALUE - value < increment ? Long.MAX_VALUE : value + increment;
    }

    private record RouteKey(AgentCoordinationScope scope, long routeId) {
        private RouteKey {
            if (scope == null || routeId <= 0) {
                throw new IllegalArgumentException("Valid coordination route is required");
            }
        }
    }

    private record ReceiptKey(long messageId, int agentCharacterId) {
    }
}
