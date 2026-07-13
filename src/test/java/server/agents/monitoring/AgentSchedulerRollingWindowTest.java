package server.agents.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentSchedulerRollingWindowTest {
    @Test
    void retainsOnlyBoundedNewestSamplesAndCalculatesPercentiles() {
        AgentSchedulerRollingWindow window = new AgentSchedulerRollingWindow(3);
        window.add(1L);
        window.add(2L);
        window.add(3L);
        window.add(4L);
        window.add(5L);

        AgentSchedulerRollingWindow.Percentiles percentiles = window.percentiles();

        assertEquals(3, percentiles.sampleCount());
        assertEquals(4L, percentiles.p50());
        assertEquals(5L, percentiles.p95());
        assertEquals(5L, percentiles.p99());
    }
}
