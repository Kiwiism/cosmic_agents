package server.agents.capabilities.combat;

import java.util.HashSet;
import java.util.Set;

public final class AgentCombatObjectiveTargetState {
    private Set<Integer> preferredMobIds = Set.of();
    private Set<Integer> fallbackMobIds = Set.of();

    public synchronized boolean setAllowedMobIds(Set<Integer> mobIds) {
        Set<Integer> next = mobIds == null ? Set.of() : Set.copyOf(mobIds);
        if (preferredMobIds.equals(next) && fallbackMobIds.isEmpty()) {
            return false;
        }
        preferredMobIds = next;
        fallbackMobIds = Set.of();
        return true;
    }

    public synchronized boolean setTargetPreferences(Set<Integer> preferred, Set<Integer> fallback) {
        Set<Integer> nextPreferred = preferred == null ? Set.of() : Set.copyOf(preferred);
        Set<Integer> nextFallback = fallback == null ? Set.of() : Set.copyOf(fallback);
        if (!nextPreferred.isEmpty() && !nextFallback.isEmpty()) {
            HashSet<Integer> deduplicated = new HashSet<>(nextFallback);
            deduplicated.removeAll(nextPreferred);
            nextFallback = Set.copyOf(deduplicated);
        }
        if (preferredMobIds.equals(nextPreferred) && fallbackMobIds.equals(nextFallback)) {
            return false;
        }
        preferredMobIds = nextPreferred;
        fallbackMobIds = nextFallback;
        return true;
    }

    public synchronized void clear() {
        preferredMobIds = Set.of();
        fallbackMobIds = Set.of();
    }

    public synchronized boolean allows(int mobId) {
        return !restricted() || preferredMobIds.contains(mobId) || fallbackMobIds.contains(mobId);
    }

    public synchronized boolean prefers(int mobId) {
        return preferredMobIds.isEmpty() || preferredMobIds.contains(mobId);
    }

    public synchronized boolean hasPreferredTargets() {
        return !preferredMobIds.isEmpty();
    }

    public synchronized boolean restricted() {
        return !preferredMobIds.isEmpty() || !fallbackMobIds.isEmpty();
    }
}
