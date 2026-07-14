package server.agents.runtime.scheduler;

import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.Comparator;
import java.util.Set;

public final class AgentLoadSheddingController {
    private final int shardId;
    private final AgentLoadSheddingConfig config;
    private final AgentLoadSheddingPolicy policy;
    private final AgentServerHealthProbe healthProbe;
    private AgentLoadSheddingState state;
    private long lastSampleMs = Long.MIN_VALUE;
    private int pressureStreak;
    private int recoveryStreak;

    public AgentLoadSheddingController(int shardId, AgentLoadSheddingConfig config) {
        this(shardId, config, new AgentDefaultLoadSheddingPolicy(), new AgentJvmServerHealthProbe());
    }

    AgentLoadSheddingController(int shardId,
                                AgentLoadSheddingConfig config,
                                AgentLoadSheddingPolicy policy,
                                AgentServerHealthProbe healthProbe) {
        if (config == null || policy == null || healthProbe == null) {
            throw new IllegalArgumentException("Agent load-shedding dependencies are required");
        }
        this.shardId = Math.max(0, shardId);
        this.config = config;
        this.policy = policy;
        this.healthProbe = healthProbe;
        this.state = AgentLoadSheddingState.normal(0L);
    }

    public AgentServerHealthSnapshot sampleServerHealth() {
        try {
            AgentServerHealthSnapshot snapshot = healthProbe.sample();
            return snapshot == null ? AgentServerHealthSnapshot.healthy() : snapshot;
        } catch (Throwable ignored) {
            return AgentServerHealthSnapshot.healthy();
        }
    }

    public boolean enabled() {
        return config.enabled();
    }

    public synchronized boolean sampleDue(long nowMs) {
        return lastSampleMs == Long.MIN_VALUE
                || nowMs < lastSampleMs
                || nowMs - lastSampleMs >= config.sampleIntervalMs();
    }

    public AgentLoadSheddingReason primaryReason() {
        return state().reasons().stream()
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElse(AgentLoadSheddingReason.READY_BACKLOG);
    }

    public synchronized AgentLoadSheddingState evaluate(AgentSchedulerPressureSample sample) {
        lastSampleMs = sample.nowMs();
        if (!config.enabled()) {
            state = AgentLoadSheddingState.normal(sample.nowMs());
            pressureStreak = 0;
            recoveryStreak = 0;
            AgentLoadSheddingRuntime.clearShard(shardId);
            return state;
        }

        AgentLoadSheddingRecommendation recommendation = policy.recommend(sample, config);
        AgentLoadSheddingLevel recommended = recommendation.level();
        AgentLoadSheddingLevel current = state.level();
        if (recommended.ordinal() > current.ordinal()) {
            recoveryStreak = 0;
            pressureStreak++;
            if (pressureStreak >= config.pressureCycles()) {
                transition(recommended, recommendation.reasons(), sample.nowMs());
            }
        } else if (recommended.ordinal() < current.ordinal()) {
            pressureStreak = 0;
            recoveryStreak++;
            if (recoveryStreak >= config.recoveryCycles()) {
                transition(current.recoverOneLevel(), Set.of(AgentLoadSheddingReason.RECOVERY_HYSTERESIS), sample.nowMs());
            }
        } else {
            pressureStreak = 0;
            recoveryStreak = 0;
            if (!state.reasons().equals(recommendation.reasons())) {
                state = new AgentLoadSheddingState(current, recommendation.reasons(), state.sinceMs(), state.epoch());
            }
        }
        AgentLoadSheddingRuntime.publish(shardId, state);
        return state;
    }

    public synchronized AgentLoadSheddingState state() {
        return state;
    }

    public boolean allows(AgentWorkClass workClass,
                          AgentPriorityClass priority,
                          AgentSimulationMode simulationMode) {
        AgentLoadSheddingLevel level = state().level();
        if (priority == AgentPriorityClass.CRITICAL || workClass == AgentWorkClass.LIFECYCLE_CRITICAL) {
            return true;
        }
        if (level.atLeast(AgentLoadSheddingLevel.SUPPRESS_COSMETIC)
                && workClass == AgentWorkClass.COSMETIC) {
            return false;
        }
        if (level.atLeast(AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM)
                && priority == AgentPriorityClass.DEFERRED) {
            return false;
        }
        return !level.atLeast(AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND)
                || priority.isVisibleOrHigher()
                || simulationMode == AgentSimulationMode.PRESENTATION;
    }

    public long effectivePeriodMs(long periodMs, AgentSimulationMode simulationMode) {
        if (!state().level().atLeast(AgentLoadSheddingLevel.REDUCE_BACKGROUND_CADENCE)
                || simulationMode == AgentSimulationMode.PRESENTATION) {
            return periodMs;
        }
        try {
            return Math.multiplyExact(periodMs, config.backgroundCadenceMultiplier());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private void transition(AgentLoadSheddingLevel next,
                            Set<AgentLoadSheddingReason> reasons,
                            long nowMs) {
        AgentLoadSheddingLevel previous = state.level();
        state = new AgentLoadSheddingState(next, reasons, nowMs, state.epoch() + 1L);
        pressureStreak = 0;
        recoveryStreak = 0;
        AgentSchedulerMetrics.recordLoadSheddingTransition(shardId, previous, state);
    }
}
