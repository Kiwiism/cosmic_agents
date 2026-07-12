package server.agents.capabilities.combat;

import java.util.Set;

public final class AgentCombatObjectiveTargetState {
    private Set<Integer> allowedMobIds = Set.of();

    public synchronized boolean setAllowedMobIds(Set<Integer> mobIds) {
        Set<Integer> next = mobIds == null ? Set.of() : Set.copyOf(mobIds);
        if (allowedMobIds.equals(next)) {
            return false;
        }
        allowedMobIds = next;
        return true;
    }

    public synchronized void clear() {
        allowedMobIds = Set.of();
    }

    public synchronized boolean allows(int mobId) {
        return allowedMobIds.isEmpty() || allowedMobIds.contains(mobId);
    }

    public synchronized boolean restricted() {
        return !allowedMobIds.isEmpty();
    }
}
