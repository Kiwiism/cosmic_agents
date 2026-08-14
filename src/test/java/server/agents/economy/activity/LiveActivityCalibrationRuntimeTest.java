package server.agents.economy.activity;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.resources.events.AgentItemQuantityChangedEvent;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveActivityCalibrationRuntimeTest {
    @Test
    void recordsOnlyObservedKillsAndExplicitConsumptions() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(123);
        when(agent.getMapId()).thenReturn(100040001);
        when(agent.getLevel()).thenReturn(25);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        LiveActivityCalibrationRuntime.begin(agent, "build-1", 1_000);
        LiveActivityCalibrationRuntime.observe(new AgentMobKilledEvent(
                123, 2_000, 100040001, 2230101, 1, 22, "grind"));
        LiveActivityCalibrationRuntime.observe(new AgentItemQuantityChangedEvent(
                123, 2_100, 2000001, 10, 9, "USE", "consume", "grind"));
        AtomicReference<ActivityCalibrationSample> written = new AtomicReference<>();

        ActivityCalibrationSample sample = LiveActivityCalibrationRuntime.end(agent, false, 61_000, written::set);

        assertEquals(1, sample.killCounts().get(2230101));
        assertEquals(1, sample.consumedItems().get(2000001));
        assertEquals("warrior", sample.jobFamily());
        assertEquals(sample, written.get());
    }

    @Test
    void retainsActiveSessionWhenDurableWriteFails() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(124);
        when(agent.getMapId()).thenReturn(100040001);
        when(agent.getLevel()).thenReturn(25);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        LiveActivityCalibrationRuntime.begin(agent, "build-1", 1_000);

        assertThrows(IllegalStateException.class, () -> LiveActivityCalibrationRuntime.end(
                agent, false, 61_000, sample -> { throw new IllegalStateException("database down"); }));

        assertNotNull(LiveActivityCalibrationRuntime.status(agent));
        LiveActivityCalibrationRuntime.end(agent, false, 62_000, sample -> { });
    }
}
