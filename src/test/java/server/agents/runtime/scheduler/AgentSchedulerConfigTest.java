package server.agents.runtime.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSchedulerConfigTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("agents.scheduler.mode");
        System.clearProperty("agents.scheduler.central.enabled");
        System.clearProperty("agents.scheduler.baseTickMs");
        System.clearProperty("agents.scheduler.logSlowTicks");
        System.clearProperty("agents.scheduler.slowTickMs");
        System.clearProperty("agents.scheduler.maxAgentsPerTick");
        System.clearProperty("agents.scheduler.ingressCapacityPerShard");
    }

    @Test
    void defaultsToLegacyAndSupportsOldCentralFlag() {
        assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, AgentSchedulerConfig.fromSystemProperties().mode());

        System.setProperty("agents.scheduler.central.enabled", "true");

        assertEquals(AgentSchedulerMode.CENTRAL_SEQUENTIAL, AgentSchedulerConfig.fromSystemProperties().mode());
    }

    @Test
    void explicitModeWinsOverCompatibilityFlag() {
        System.setProperty("agents.scheduler.central.enabled", "true");
        System.setProperty("agents.scheduler.mode", "legacy");

        assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, AgentSchedulerConfig.fromSystemProperties().mode());

        System.setProperty("agents.scheduler.mode", "central-sharded");
        assertEquals(AgentSchedulerMode.CENTRAL_SHARDED, AgentSchedulerConfig.fromSystemProperties().mode());
    }

    @Test
    void rejectsUnknownModesAndUnsafeValues() {
        System.setProperty("agents.scheduler.mode", "surprise");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.mode", "legacy");
        System.setProperty("agents.scheduler.baseTickMs", "9");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.baseTickMs", "50");
        System.setProperty("agents.scheduler.maxAgentsPerTick", "-1");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.maxAgentsPerTick", "0");
        System.setProperty("agents.scheduler.ingressCapacityPerShard", "0");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);
    }
}
