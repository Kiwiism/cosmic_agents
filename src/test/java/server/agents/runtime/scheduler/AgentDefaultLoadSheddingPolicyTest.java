package server.agents.runtime.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDefaultLoadSheddingPolicyTest {
    private final AgentDefaultLoadSheddingPolicy policy = new AgentDefaultLoadSheddingPolicy();
    private final AgentLoadSheddingConfig config = config(1, 2);

    @Test
    void mapsSchedulerPressureToExplicitLevelsAndReasons() {
        AgentLoadSheddingRecommendation normal = policy.recommend(sample(0L, 0, true), config);
        AgentLoadSheddingRecommendation cosmetic = policy.recommend(sample(100L, 0, true), config);
        AgentLoadSheddingRecommendation admission = policy.recommend(sample(1_600L, 0, true), config);

        assertEquals(AgentLoadSheddingLevel.NORMAL, normal.level());
        assertEquals(AgentLoadSheddingLevel.SUPPRESS_COSMETIC, cosmetic.level());
        assertTrue(cosmetic.reasons().contains(AgentLoadSheddingReason.QUEUE_LAG));
        assertEquals(AgentLoadSheddingLevel.ADMISSION_CONTROL, admission.level());
    }

    @Test
    void unhealthyPlayerPathForcesAdmissionControl() {
        AgentLoadSheddingRecommendation recommendation = policy.recommend(sample(0L, 0, false), config);

        assertEquals(AgentLoadSheddingLevel.ADMISSION_CONTROL, recommendation.level());
        assertTrue(recommendation.reasons().contains(AgentLoadSheddingReason.PLAYER_PATH_UNHEALTHY));
    }

    @Test
    void gcPressureUsesBoundedMultiplesOfConfiguredThreshold() {
        AgentSchedulerPressureSample levelThree = new AgentSchedulerPressureSample(
                1_000L, 0L, 10, 0, 100, 0,
                new AgentServerHealthSnapshot(0.0d, 0.0d, 250L, true));
        AgentSchedulerPressureSample levelFive = new AgentSchedulerPressureSample(
                1_000L, 0L, 10, 0, 100, 0,
                new AgentServerHealthSnapshot(0.0d, 0.0d, 1_000L, true));

        assertEquals(AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM,
                policy.recommend(levelThree, config).level());
        assertEquals(AgentLoadSheddingLevel.ADMISSION_CONTROL,
                policy.recommend(levelFive, config).level());
    }

    static AgentLoadSheddingConfig config(int pressureCycles, int recoveryCycles) {
        return new AgentLoadSheddingConfig(
                true,
                pressureCycles,
                recoveryCycles,
                1L,
                100L,
                8,
                75,
                85.0d,
                85.0d,
                250L,
                2,
                2_000);
    }

    static AgentSchedulerPressureSample sample(long queueLagMs, int readyDepth, boolean playerPathHealthy) {
        return new AgentSchedulerPressureSample(
                1_000L,
                queueLagMs,
                10,
                0,
                100,
                readyDepth,
                new AgentServerHealthSnapshot(0.0d, 0.0d, 0L, playerPathHealthy));
    }
}
