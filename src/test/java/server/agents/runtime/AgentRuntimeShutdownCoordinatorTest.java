package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.scheduler.AgentScheduler;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeShutdownCoordinatorTest {
    @BeforeEach
    void setUp() {
        AgentRuntimeRegistry.clear();
        AgentRuntimeShutdownCoordinator.start();
    }

    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
        AgentRuntimeShutdownCoordinator.start();
    }

    @Test
    void shutdownCancelsLiveSessionAndIsIdempotent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(101);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        ScheduledFuture<?> scheduledTask = mock(ScheduledFuture.class);
        when(scheduledTask.isCancelled()).thenReturn(true);
        entry.scheduledTaskState().attachScheduledTask(scheduledTask);
        AgentRuntimeRegistry.registerEntry(1, entry);

        AgentRuntimeShutdownCoordinator.Report report =
                AgentRuntimeShutdownCoordinator.shutdown(Duration.ofSeconds(1));

        assertEquals(1, report.sessionsObserved());
        assertEquals(1, report.scheduleCancellationsRequested());
        assertTrue(report.failedSessionIds().isEmpty());
        assertEquals(0, report.schedulerRegistrationsRemaining());
        assertFalse(report.timedOut());
        assertFalse(AgentRuntimeShutdownCoordinator.acceptingRegistrations());
        verify(scheduledTask).cancel(false);
        assertThrows(RejectedExecutionException.class,
                () -> AgentScheduler.register(entry, () -> { }, 50L,
                        (tick, periodMs) -> mock(ScheduledFuture.class)));
        assertSame(report, AgentRuntimeShutdownCoordinator.shutdown(Duration.ofSeconds(1)));
    }

    @Test
    void startReopensRegistrationAndAsyncAdmissionAfterCleanShutdown() {
        AgentRuntimeShutdownCoordinator.shutdown(Duration.ofSeconds(1));

        AgentRuntimeShutdownCoordinator.start();

        assertTrue(AgentRuntimeShutdownCoordinator.acceptingRegistrations());
        assertTrue(AgentAsyncExecutorRegistry.runtime().accepting());
    }
}
