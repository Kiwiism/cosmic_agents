package server.agents.capabilities.runtime;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-Agent, all-or-nothing resource leases. Re-entrant ownership lets a
 * parent capability hand off to a child under the same correlation id.
 */
public final class AgentCapabilityResourceLockState {
    public static final AgentCapabilityStateKey<AgentCapabilityResourceLockState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.capability-resource-locks",
                    AgentCapabilityResourceLockState.class,
                    AgentCapabilityResourceLockState::new);

    private final Map<AgentCapabilityResource, Lease> leases =
            new EnumMap<>(AgentCapabilityResource.class);

    public synchronized boolean acquire(
            String ownerId,
            Set<AgentCapabilityResource> resources,
            long nowMs,
            long expiresAtMs) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Resource lock owner is required");
        }
        expire(nowMs);
        Set<AgentCapabilityResource> requested =
                resources == null ? Set.of() : Set.copyOf(resources);
        for (AgentCapabilityResource resource : requested) {
            Lease current = leases.get(resource);
            if (current != null && !current.ownerId.equals(ownerId)) {
                return false;
            }
        }
        for (AgentCapabilityResource resource : requested) {
            Lease current = leases.get(resource);
            if (current == null) {
                leases.put(resource, new Lease(ownerId, 1, expiresAtMs));
            } else {
                leases.put(resource, new Lease(ownerId, current.depth + 1,
                        Math.max(current.expiresAtMs, expiresAtMs)));
            }
        }
        return true;
    }

    public synchronized void release(
            String ownerId, Set<AgentCapabilityResource> resources) {
        if (ownerId == null || ownerId.isBlank() || resources == null) {
            return;
        }
        for (AgentCapabilityResource resource : resources) {
            Lease current = leases.get(resource);
            if (current == null || !current.ownerId.equals(ownerId)) {
                continue;
            }
            if (current.depth <= 1) {
                leases.remove(resource);
            } else {
                leases.put(resource, new Lease(ownerId, current.depth - 1,
                        current.expiresAtMs));
            }
        }
    }

    public synchronized void releaseOwner(String ownerId) {
        if (ownerId != null) {
            leases.entrySet().removeIf(entry -> entry.getValue().ownerId.equals(ownerId));
        }
    }

    public synchronized Map<AgentCapabilityResource, String> owners(long nowMs) {
        expire(nowMs);
        Map<AgentCapabilityResource, String> result =
                new EnumMap<>(AgentCapabilityResource.class);
        leases.forEach((resource, lease) -> result.put(resource, lease.ownerId));
        return Map.copyOf(result);
    }

    public synchronized int size(long nowMs) {
        expire(nowMs);
        return leases.size();
    }

    private void expire(long nowMs) {
        leases.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= nowMs);
    }

    private record Lease(String ownerId, int depth, long expiresAtMs) {
    }
}
