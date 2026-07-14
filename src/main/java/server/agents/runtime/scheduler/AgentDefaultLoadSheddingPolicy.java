package server.agents.runtime.scheduler;

import java.util.EnumSet;
import java.util.Set;

public final class AgentDefaultLoadSheddingPolicy implements AgentLoadSheddingPolicy {
    @Override
    public AgentLoadSheddingRecommendation recommend(
            AgentSchedulerPressureSample sample,
            AgentLoadSheddingConfig config) {
        AgentLoadSheddingLevel level = AgentLoadSheddingLevel.NORMAL;
        Set<AgentLoadSheddingReason> reasons = EnumSet.noneOf(AgentLoadSheddingReason.class);

        AgentLoadSheddingLevel queueLag = scaledLevel(sample.queueLagP95Ms(), config.queueLagLevel1Ms());
        if (queueLag != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, queueLag);
            reasons.add(AgentLoadSheddingReason.QUEUE_LAG);
        }
        AgentLoadSheddingLevel ready = scaledLevel(sample.actionableReadyDepth(), config.readyDepthLevel1());
        if (ready != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, ready);
            reasons.add(AgentLoadSheddingReason.READY_BACKLOG);
        }
        AgentLoadSheddingLevel ingress = ingressLevel(sample.ingressPercent(), config.ingressLevel2Percent());
        if (ingress != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, ingress);
            reasons.add(AgentLoadSheddingReason.INGRESS_PRESSURE);
        }

        AgentServerHealthSnapshot health = sample.serverHealth();
        AgentLoadSheddingLevel cpu = healthLevel(health.processCpuPercent(), config.cpuLevel3Percent());
        if (cpu != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, cpu);
            reasons.add(AgentLoadSheddingReason.PROCESS_CPU);
        }
        AgentLoadSheddingLevel heap = healthLevel(health.heapUsedPercent(), config.heapLevel3Percent());
        if (heap != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, heap);
            reasons.add(AgentLoadSheddingReason.HEAP_PRESSURE);
        }
        AgentLoadSheddingLevel gc = gcLevel(health.gcCollectionDeltaMs(), config.gcPauseLevel3Ms());
        if (gc != AgentLoadSheddingLevel.NORMAL) {
            level = maximum(level, gc);
            reasons.add(AgentLoadSheddingReason.GC_PAUSE);
        }
        if (!health.playerPathHealthy()) {
            level = AgentLoadSheddingLevel.ADMISSION_CONTROL;
            reasons.add(AgentLoadSheddingReason.PLAYER_PATH_UNHEALTHY);
        }
        return new AgentLoadSheddingRecommendation(level, reasons);
    }

    private static AgentLoadSheddingLevel scaledLevel(long value, long levelOneThreshold) {
        if (value < levelOneThreshold) {
            return AgentLoadSheddingLevel.NORMAL;
        }
        long ratio = Math.max(1L, value / levelOneThreshold);
        int level = 1;
        while (ratio >= 2L && level < AgentLoadSheddingLevel.ADMISSION_CONTROL.ordinal()) {
            ratio /= 2L;
            level++;
        }
        return AgentLoadSheddingLevel.values()[level];
    }

    private static AgentLoadSheddingLevel ingressLevel(int percent, int levelTwoThreshold) {
        if (percent < levelTwoThreshold) {
            return AgentLoadSheddingLevel.NORMAL;
        }
        int remaining = Math.max(1, 100 - levelTwoThreshold);
        int offset = percent - levelTwoThreshold;
        int band = Math.min(3, offset * 4 / remaining);
        return AgentLoadSheddingLevel.values()[AgentLoadSheddingLevel.REDUCE_BACKGROUND_CADENCE.ordinal() + band];
    }

    private static AgentLoadSheddingLevel healthLevel(double value, double levelThreeThreshold) {
        if (value < levelThreeThreshold) {
            return AgentLoadSheddingLevel.NORMAL;
        }
        double remaining = Math.max(0.1d, 100.0d - levelThreeThreshold);
        int band = Math.min(2, (int) ((value - levelThreeThreshold) * 3.0d / remaining));
        return AgentLoadSheddingLevel.values()[AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM.ordinal() + band];
    }

    private static AgentLoadSheddingLevel gcLevel(long value, long levelThreeThreshold) {
        if (value < levelThreeThreshold) {
            return AgentLoadSheddingLevel.NORMAL;
        }
        if (value >= saturatedMultiply(levelThreeThreshold, 4L)) {
            return AgentLoadSheddingLevel.ADMISSION_CONTROL;
        }
        if (value >= saturatedMultiply(levelThreeThreshold, 2L)) {
            return AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND;
        }
        return AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static AgentLoadSheddingLevel maximum(
            AgentLoadSheddingLevel first,
            AgentLoadSheddingLevel second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
