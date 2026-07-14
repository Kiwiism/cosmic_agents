package server.agents.runtime.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentLoadSheddingConfigTest {
    @AfterEach
    void tearDown() {
        System.clearProperty("agents.scheduler.loadShedding.enabled");
        System.clearProperty("agents.scheduler.loadShedding.sampleIntervalMs");
        System.clearProperty("agents.scheduler.loadShedding.maxActiveAgents");
    }

    @Test
    void defaultsRemainDisabledAndBoundedForTargetPopulation() {
        AgentLoadSheddingConfig config = AgentLoadSheddingConfig.fromSystemProperties();

        assertFalse(config.enabled());
        assertEquals(1_000L, config.sampleIntervalMs());
        assertEquals(2_000, config.maxActiveAgents());
    }

    @Test
    void readsExplicitSamplingAndPopulationLimits() {
        System.setProperty("agents.scheduler.loadShedding.enabled", "true");
        System.setProperty("agents.scheduler.loadShedding.sampleIntervalMs", "250");
        System.setProperty("agents.scheduler.loadShedding.maxActiveAgents", "500");

        AgentLoadSheddingConfig config = AgentLoadSheddingConfig.fromSystemProperties();
        assertEquals(true, config.enabled());
        assertEquals(250L, config.sampleIntervalMs());
        assertEquals(500, config.maxActiveAgents());
    }

    @Test
    void rejectsInvalidThresholdRelationships() {
        assertThrows(IllegalArgumentException.class, () -> new AgentLoadSheddingConfig(
                true, 1, 1, 1L, 100L, 8, 99, 101.0d, 85.0d, 250L, 2, 2_000));
    }
}
