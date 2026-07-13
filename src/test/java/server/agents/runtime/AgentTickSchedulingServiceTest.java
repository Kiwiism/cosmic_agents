package server.agents.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentSchedulerMode;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTickSchedulingServiceTest {
    @AfterEach
    void clearFlag() {
        System.clearProperty("agents.scheduler.central.enabled");
    }

    @Test
    void disabledCentralSchedulerPreservesLegacyRegistrationPath() {
        System.clearProperty("agents.scheduler.central.enabled");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        ScheduledFuture<?> expected = mock(ScheduledFuture.class);
        AtomicBoolean legacyCalled = new AtomicBoolean();

        AgentScheduleHandle actual = AgentTickSchedulingService.register(
                entry,
                () -> { },
                50L,
                (tick, period) -> {
                    legacyCalled.set(true);
                    return expected;
                });

        assertTrue(legacyCalled.get());
        assertEquals(AgentSchedulerMode.LEGACY_PER_AGENT, actual.mode());
        assertEquals(entry.sessionGeneration(), actual.sessionId().generation());
    }
}
