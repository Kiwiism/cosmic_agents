package server.agents.capabilities.looting;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.LinkedHashMap;
import java.util.Set;

/** Ephemeral record of recent kills whose drops may still need collection. */
public final class AgentPostKillLootState {
    public static final AgentCapabilityStateKey<AgentPostKillLootState> STATE_KEY =
            new AgentCapabilityStateKey<>("looting.post-kill",
                    AgentPostKillLootState.class, AgentPostKillLootState::new);

    private final LinkedHashMap<Integer, Long> killedObjectIds = new LinkedHashMap<>();

    public synchronized void recordKill(int mobObjectId, long occurredAtMs) {
        killedObjectIds.put(mobObjectId, occurredAtMs);
        while (killedObjectIds.size() > AgentLootCollectionPolicyConfig.maxTrackedKills()) {
            killedObjectIds.remove(killedObjectIds.keySet().iterator().next());
        }
    }

    public synchronized Snapshot snapshot(long nowMs) {
        prune(nowMs);
        long oldestAtMs = killedObjectIds.values().stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0L);
        return new Snapshot(Set.copyOf(killedObjectIds.keySet()),
                killedObjectIds.size(), oldestAtMs);
    }

    public synchronized void resolveKill(int mobObjectId) {
        killedObjectIds.remove(mobObjectId);
    }

    public synchronized void clear() {
        killedObjectIds.clear();
    }

    private void prune(long nowMs) {
        long cutoff = nowMs - AgentLootCollectionPolicyConfig.trackedKillLifetimeMs();
        killedObjectIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public record Snapshot(Set<Integer> killedObjectIds, int killCount, long oldestKillAtMs) {
        public boolean hasKills() {
            return killCount > 0;
        }
    }
}
