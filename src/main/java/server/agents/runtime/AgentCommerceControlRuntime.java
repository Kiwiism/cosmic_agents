package server.agents.runtime;

import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Process-local lease granting the Commerce system exclusive control of an Agent character. */
public final class AgentCommerceControlRuntime {
    private static final Map<Integer, Lease> LEASES = new HashMap<>();

    private AgentCommerceControlRuntime() {
    }

    public static synchronized void claim(int characterId, String owner) {
        if (characterId <= 0) throw new IllegalArgumentException("characterId must be positive");
        String normalized = Objects.requireNonNull(owner, "owner").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("owner must not be blank");
        Lease existing = LEASES.putIfAbsent(characterId, new Lease(normalized, null));
        if (existing != null && !existing.owner().equals(normalized)) {
            throw new IllegalStateException("agent character " + characterId
                    + " is already controlled by " + existing.owner());
        }
    }

    public static synchronized void attribute(int characterId, EconomyOperationMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Lease lease = LEASES.get(characterId);
        if (lease == null) throw new IllegalStateException("Agent is not controlled by Commerce");
        String expectedOwner = metadata.runId() == null ? "" : "economy:" + metadata.runId();
        if (!lease.owner().equals(expectedOwner)) {
            throw new IllegalStateException("economy attribution does not match Commerce owner");
        }
        LEASES.put(characterId, new Lease(lease.owner(), metadata));
    }

    public static <T> T withAttribution(int characterId, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        EconomyOperationMetadata metadata;
        synchronized (AgentCommerceControlRuntime.class) {
            Lease lease = LEASES.get(characterId);
            metadata = lease == null ? null : lease.metadata();
        }
        return metadata == null ? action.get() : EconomyOperationContext.with(metadata, action);
    }

    public static synchronized boolean claimed(int characterId) {
        return LEASES.containsKey(characterId);
    }

    public static synchronized boolean ownedBy(int characterId, String owner) {
        Lease lease = LEASES.get(characterId);
        return lease != null && Objects.equals(lease.owner(), owner);
    }

    public static synchronized void release(String owner) {
        if (owner == null) return;
        LEASES.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
    }

    public static synchronized void releaseCharacter(int characterId, String owner) {
        Lease lease = LEASES.get(characterId);
        if (lease != null && Objects.equals(lease.owner(), owner)) {
            LEASES.remove(characterId);
        }
    }

    static synchronized void clearForTests() {
        LEASES.clear();
    }

    private record Lease(String owner, EconomyOperationMetadata metadata) { }
}
