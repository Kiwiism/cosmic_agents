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
        System.clearProperty("agents.scheduler.cycleBudgetMs");
        System.clearProperty("agents.scheduler.maxWorkItemsPerCycle");
        System.clearProperty("agents.scheduler.visibleReservePercent");
        System.clearProperty("agents.scheduler.criticalReservePercent");
        System.clearProperty("agents.scheduler.starvationPromotionMs");
        System.clearProperty("agents.scheduler.shardCount");
        System.clearProperty("agents.scheduler.simulation.enabled");
        System.clearProperty("agents.scheduler.simulation.backgroundAbstract.enabled");
        System.clearProperty("agents.scheduler.simulation.backgroundActiveTickMs");
        System.clearProperty("agents.scheduler.simulation.backgroundAbstractHeartbeatMs");
        System.clearProperty("agents.scheduler.simulation.backgroundMaxWorkPerMapPerCycle");
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
    void validatesConfiguredShardCount() {
        System.setProperty("agents.scheduler.shardCount", "3");
        assertEquals(3, AgentSchedulerConfig.fromSystemProperties().shardCount());

        System.setProperty("agents.scheduler.shardCount", "0");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);
    }

    @Test
    void simulationCadenceDefaultsOffAndValidatesOrdering() {
        AgentSchedulerConfig defaults = AgentSchedulerConfig.fromSystemProperties();
        assertEquals(false, defaults.simulationEnabled());
        assertEquals(false, defaults.backgroundAbstractEnabled());
        assertEquals(250L, defaults.backgroundActiveTickMs());
        assertEquals(5_000L, defaults.backgroundAbstractHeartbeatMs());
        assertEquals(32, defaults.backgroundMaxWorkPerMapPerCycle());

        System.setProperty("agents.scheduler.baseTickMs", "500");
        AgentSchedulerConfig slowerBase = AgentSchedulerConfig.fromSystemProperties();
        assertEquals(500L, slowerBase.backgroundActiveTickMs());
        assertEquals(5_000L, slowerBase.backgroundAbstractHeartbeatMs());

        System.setProperty("agents.scheduler.baseTickMs", "50");
        System.setProperty("agents.scheduler.simulation.enabled", "true");
        System.setProperty("agents.scheduler.simulation.backgroundActiveTickMs", "49");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.simulation.backgroundActiveTickMs", "250");
        System.setProperty("agents.scheduler.simulation.backgroundAbstractHeartbeatMs", "249");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.simulation.backgroundAbstractHeartbeatMs", "5000");
        System.setProperty("agents.scheduler.simulation.backgroundMaxWorkPerMapPerCycle", "-1");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);
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

        System.setProperty("agents.scheduler.ingressCapacityPerShard", "4096");
        System.setProperty("agents.scheduler.cycleBudgetMs", "0");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);

        System.setProperty("agents.scheduler.cycleBudgetMs", "10");
        System.setProperty("agents.scheduler.visibleReservePercent", "91");
        System.setProperty("agents.scheduler.criticalReservePercent", "10");
        assertThrows(IllegalArgumentException.class, AgentSchedulerConfig::fromSystemProperties);
    }
}
