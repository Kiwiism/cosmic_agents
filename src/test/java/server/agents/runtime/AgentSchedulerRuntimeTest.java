package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSchedulerRuntimeTest {
    @Test
    void randomDelayUsesLegacyInclusiveExclusiveWindow() {
        for (int i = 0; i < 100; i++) {
            long delayMs = AgentSchedulerRuntime.randomDelayMs(500, 700);

            assertTrue(delayMs >= 500);
            assertTrue(delayMs < 700);
        }
    }
}
