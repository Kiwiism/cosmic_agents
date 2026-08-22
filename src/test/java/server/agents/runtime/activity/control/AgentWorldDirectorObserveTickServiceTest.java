package server.agents.runtime.activity.control;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldShadowEvaluator;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentWorldDirectorObserveTickServiceTest {
    @Test
    void samplesOnlyAtConfiguredSchedulerCadence() {
        AgentWorldDirectorObserveState state = new AgentWorldDirectorObserveState();
        state.configure(AgentWorldDirectorMode.OBSERVE, 5_000L);

        assertNotNull(AgentWorldDirectorObserveTickService.tick(
                state, AgentWorldShadowEvaluator.baseline(), context(1_000L), 1_000L));
        assertNull(AgentWorldDirectorObserveTickService.tick(
                state, AgentWorldShadowEvaluator.baseline(), context(2_000L), 2_000L));
        assertNotNull(AgentWorldDirectorObserveTickService.tick(
                state, AgentWorldShadowEvaluator.baseline(), context(6_000L), 6_000L));
        assertEquals(2L, state.snapshot().sampleCount());
    }

    private static AgentWorldContext context(long nowMs) {
        return new AgentWorldContext(nowMs, nowMs, 27, "KiwiAgent", 15, 100, 100000000,
                100, 100, 50, 50, 1_000L, true, false, Set.of(), Set.of(),
                null, "", "", "", "FIRST_JOB", Map.of());
    }
}
