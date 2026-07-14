package server.agents.runtime.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.simulation.AgentSimulationMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoadSheddingControllerTest {
    @AfterEach
    void tearDown() {
        AgentLoadSheddingRuntime.resetForTests();
    }

    @Test
    void escalatesAfterPressureStreakAndRecoversOneLevelAfterHysteresis() {
        AgentLoadSheddingController controller = controller(2, 2);
        AgentSchedulerPressureSample severe = AgentDefaultLoadSheddingPolicyTest.sample(1_600L, 0, true);
        AgentSchedulerPressureSample healthy = AgentDefaultLoadSheddingPolicyTest.sample(0L, 0, true);

        assertEquals(AgentLoadSheddingLevel.NORMAL, controller.evaluate(severe).level());
        assertEquals(AgentLoadSheddingLevel.ADMISSION_CONTROL, controller.evaluate(severe).level());
        assertEquals(AgentLoadSheddingLevel.ADMISSION_CONTROL, controller.evaluate(healthy).level());
        assertEquals(AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND,
                controller.evaluate(healthy).level());
    }

    @Test
    void neverSuppressesCriticalAndOnlySlowsBackgroundCadence() {
        AgentLoadSheddingController controller = controller(1, 2);
        controller.evaluate(AgentDefaultLoadSheddingPolicyTest.sample(800L, 0, true));

        assertEquals(AgentLoadSheddingLevel.PAUSE_LOW_PRIORITY_BACKGROUND, controller.state().level());
        assertTrue(controller.allows(
                AgentWorkClass.LIFECYCLE_CRITICAL,
                AgentPriorityClass.CRITICAL,
                AgentSimulationMode.BACKGROUND_ACTIVE));
        assertFalse(controller.allows(
                AgentWorkClass.BACKGROUND_GAMEPLAY,
                AgentPriorityClass.BACKGROUND_ACTIVE,
                AgentSimulationMode.BACKGROUND_ACTIVE));
        assertEquals(100L, controller.effectivePeriodMs(50L, AgentSimulationMode.BACKGROUND_ACTIVE));
        assertEquals(50L, controller.effectivePeriodMs(50L, AgentSimulationMode.PRESENTATION));
    }

    @Test
    void pressureSamplingStaysOffTheHotPathBetweenIntervals() {
        AgentLoadSheddingController controller = controller(1, 2);

        assertTrue(controller.sampleDue(1_000L));
        controller.evaluate(AgentDefaultLoadSheddingPolicyTest.sample(0L, 0, true));
        assertFalse(controller.sampleDue(1_000L));
        assertTrue(controller.sampleDue(1_001L));
    }

    private static AgentLoadSheddingController controller(int pressureCycles, int recoveryCycles) {
        return new AgentLoadSheddingController(
                0,
                AgentDefaultLoadSheddingPolicyTest.config(pressureCycles, recoveryCycles),
                new AgentDefaultLoadSheddingPolicy(),
                AgentServerHealthSnapshot::healthy);
    }
}
