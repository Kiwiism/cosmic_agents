package server.agents.runtime;

import server.agents.administration.AgentCleanSlateResetService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes offline character loading with destructive administrative maintenance. */
public final class AgentCharacterMaintenanceRuntime {
    private static final ConcurrentHashMap<Integer, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private AgentCharacterMaintenanceRuntime() {
    }

    public static AgentCleanSlateResetService.MaintenanceLease acquire(int characterId) {
        if (characterId <= 0) throw new IllegalArgumentException("positive character id is required");
        ReentrantLock lock = LOCKS.computeIfAbsent(characterId, ignored -> new ReentrantLock(true));
        lock.lock();
        return lock::unlock;
    }
}
