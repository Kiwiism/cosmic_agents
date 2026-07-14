package server.agents.runtime.scheduler;

import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.async.AgentAsyncWorkKind;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentLoadSheddingRuntime {
    private static final Map<Integer, AgentLoadSheddingState> STATES_BY_SHARD = new ConcurrentHashMap<>();

    private AgentLoadSheddingRuntime() {
    }

    static void publish(int shardId, AgentLoadSheddingState state) {
        int normalizedShardId = Math.max(0, shardId);
        STATES_BY_SHARD.put(normalizedShardId, state);
        AgentSchedulerMetrics.recordLoadSheddingState(normalizedShardId, state);
    }

    public static void clearShard(int shardId) {
        int normalizedShardId = Math.max(0, shardId);
        STATES_BY_SHARD.remove(normalizedShardId);
        AgentSchedulerMetrics.clearLoadSheddingShard(normalizedShardId);
    }

    public static AgentLoadSheddingState globalState() {
        AgentLoadSheddingLevel strongest = STATES_BY_SHARD.values().stream()
                .map(AgentLoadSheddingState::level)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(AgentLoadSheddingLevel.NORMAL);
        if (strongest == AgentLoadSheddingLevel.NORMAL && STATES_BY_SHARD.isEmpty()) {
            return AgentLoadSheddingState.normal(0L);
        }
        Set<AgentLoadSheddingReason> reasons = EnumSet.noneOf(AgentLoadSheddingReason.class);
        long sinceMs = Long.MAX_VALUE;
        long epoch = 0L;
        for (AgentLoadSheddingState state : STATES_BY_SHARD.values()) {
            if (state.level() != strongest) {
                continue;
            }
            reasons.addAll(state.reasons());
            sinceMs = Math.min(sinceMs, state.sinceMs());
            epoch = Math.max(epoch, state.epoch());
        }
        return new AgentLoadSheddingState(
                strongest,
                reasons,
                sinceMs == Long.MAX_VALUE ? 0L : sinceMs,
                epoch);
    }

    public static boolean permitsDialogue(boolean leaderDirected) {
        if (leaderDirected || !globalState().level().atLeast(AgentLoadSheddingLevel.SUPPRESS_COSMETIC)) {
            return true;
        }
        recordSuppressed();
        return false;
    }

    public static boolean permitsAsync(AgentAsyncWorkKind kind) {
        if (!globalState().level().atLeast(AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM)) {
            return true;
        }
        boolean permitted = kind != AgentAsyncWorkKind.LLM_NETWORK
                && kind != AgentAsyncWorkKind.CATALOG_REBUILD
                && kind != AgentAsyncWorkKind.ECONOMY_ANALYSIS;
        if (!permitted) {
            recordSuppressed();
        }
        return permitted;
    }

    public static AgentAdmissionDecision admissionDecision(boolean replacement, int activeAgents) {
        AgentLoadSheddingConfig config = AgentLoadSheddingConfig.fromSystemProperties();
        if (!config.enabled() || replacement) {
            return AgentAdmissionDecision.allow();
        }
        AgentLoadSheddingState state = globalState();
        if (state.level().atLeast(AgentLoadSheddingLevel.ADMISSION_CONTROL)) {
            AgentLoadSheddingReason reason = state.reasons().stream()
                    .min(Comparator.comparingInt(Enum::ordinal))
                    .orElse(AgentLoadSheddingReason.READY_BACKLOG);
            AgentSchedulerMetrics.recordAgentAdmissionRejected(reason);
            return AgentAdmissionDecision.reject(
                    reason,
                    "Agent admission is temporarily paused while the server recovers.");
        }
        if (config.maxActiveAgents() > 0 && activeAgents >= config.maxActiveAgents()) {
            AgentSchedulerMetrics.recordAgentAdmissionRejected(AgentLoadSheddingReason.POPULATION_LIMIT);
            return AgentAdmissionDecision.reject(
                    AgentLoadSheddingReason.POPULATION_LIMIT,
                    "Agent population limit reached: " + config.maxActiveAgents());
        }
        return AgentAdmissionDecision.allow();
    }

    static void resetForTests() {
        STATES_BY_SHARD.keySet().forEach(AgentSchedulerMetrics::clearLoadSheddingShard);
        STATES_BY_SHARD.clear();
    }

    private static void recordSuppressed() {
        AgentLoadSheddingState state = globalState();
        AgentLoadSheddingReason reason = state.reasons().stream()
                .findFirst()
                .orElse(AgentLoadSheddingReason.READY_BACKLOG);
        AgentSchedulerMetrics.recordLoadSheddingSuppressed(reason);
    }
}
