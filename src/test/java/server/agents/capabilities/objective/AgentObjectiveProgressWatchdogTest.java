package server.agents.capabilities.objective;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentObjectiveProgressWatchdogTest {
    @Test
    void inactivityNudgesThenRecoversButMeaningfulApproachRestartsTheClock() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentObjectiveProgressWatchdog.State state = new AgentObjectiveProgressWatchdog.State();
        AgentObjectiveRecoveryPolicy policy =
                new AgentObjectiveRecoveryPolicy(5_000L, 15_000L, 3, 500L);
        when(agent.getMapId()).thenReturn(30_000);
        when(agent.getLevel()).thenReturn(1);
        when(agent.getExp()).thenReturn(0);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(
                entry, new Point(100, 20));

        AgentObjectiveProgressWatchdog.start(state, entry, agent, 1_000L);

        assertEquals(AgentObjectiveProgressWatchdog.Action.NONE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 5_999L, policy).action());
        assertEquals(AgentObjectiveProgressWatchdog.Action.NUDGE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 6_000L, policy).action());

        when(agent.getPosition()).thenReturn(new Point(30, 20));
        assertEquals(AgentObjectiveProgressWatchdog.Action.NONE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 10_000L, policy).action());
        assertEquals(AgentObjectiveProgressWatchdog.Action.NUDGE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 15_000L, policy).action());
        assertEquals(AgentObjectiveProgressWatchdog.Action.RECOVER,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 25_000L, policy).action());
    }

    @Test
    void alternatingNavigationTargetsDoNotHideAStall() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentObjectiveProgressWatchdog.State state = new AgentObjectiveProgressWatchdog.State();
        AgentObjectiveRecoveryPolicy policy =
                new AgentObjectiveRecoveryPolicy(5_000L, 15_000L, 3, 500L);
        when(agent.getMapId()).thenReturn(30_000);
        when(agent.getLevel()).thenReturn(1);
        when(agent.getExp()).thenReturn(0);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(
                entry, new Point(100, 20));
        AgentObjectiveProgressWatchdog.start(state, entry, agent, 1_000L);

        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(
                entry, new Point(10, 100));
        assertEquals(AgentObjectiveProgressWatchdog.Action.NONE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 2_000L, policy).action());

        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(
                entry, new Point(100, 20));
        assertEquals(AgentObjectiveProgressWatchdog.Action.NUDGE,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 7_000L, policy).action());
        assertEquals(AgentObjectiveProgressWatchdog.Action.RECOVER,
                AgentObjectiveProgressWatchdog.evaluate(
                        state, entry, agent, 17_000L, policy).action());
    }
}
