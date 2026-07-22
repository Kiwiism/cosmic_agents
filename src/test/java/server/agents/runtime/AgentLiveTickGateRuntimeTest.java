package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.plans.AgentPlanReattachmentRuntime;
import server.agents.runtime.maintenance.AgentMaintenanceSupervisor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLiveTickGateRuntimeTest {
    @Test
    void objectiveSupervisionRunsBeforeCommonTickSystems() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentPlanReattachmentRuntime> reattachment =
                     mockStatic(AgentPlanReattachmentRuntime.class);
             MockedStatic<AgentMaintenanceSupervisor> maintenance =
                     mockStatic(AgentMaintenanceSupervisor.class)) {
            reattachment.when(() -> AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, 100L))
                    .thenAnswer(invocation -> {
                        calls.add("reattach");
                        return false;
                    });
            maintenance.when(() -> AgentMaintenanceSupervisor.tickRuntime(entry, agent, 100L))
                    .thenAnswer(invocation -> {
                        calls.add("maintenance");
                        return true;
                    });

            assertTrue(AgentLiveTickGateRuntime.tickObjectiveSupervision(entry, agent, 100L));
            assertEquals(List.of("reattach", "maintenance"), calls);
        }
    }

    @Test
    void commonTickConsumptionShortCircuitsRemainingLiveGates() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(-1);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentMapTransitionRuntime> mapTransition = mockStatic(AgentMapTransitionRuntime.class);
             MockedStatic<AgentCommonTickRuntime> commonTick = mockStatic(AgentCommonTickRuntime.class);
             MockedStatic<AgentPlanReattachmentRuntime> reattachment =
                     mockStatic(AgentPlanReattachmentRuntime.class);
             MockedStatic<AgentMaintenanceSupervisor> maintenance =
                     mockStatic(AgentMaintenanceSupervisor.class)) {
            mapTransition.when(() -> AgentMapTransitionRuntime.handleTrackedMapChange(
                    any(AgentRuntimeEntry.class), any(Character.class), any(), any()))
                    .thenReturn(false);
            commonTick.when(() -> AgentCommonTickRuntime.runCommonTickSystems(
                            any(AgentRuntimeEntry.class),
                            any(Character.class),
                            any(Character.class),
                            anyBoolean(),
                            any()))
                    .thenAnswer(invocation -> {
                        calls.add("common");
                        return true;
                    });

            boolean consumed = AgentLiveTickGateRuntime.tickLiveGates(
                    new AgentLiveTickGateService.Context(
                            entry,
                            agent,
                            leader,
                            followAnchor,
                            new Point(10, 20),
                            true),
                    false,
                    tickEntry -> calls.add("script"),
                    grindEntry -> calls.add("grind"),
                    followEntry -> calls.add("follow"),
                    650,
                    1200,
                    2);

            assertTrue(consumed);
            assertEquals(List.of("common"), calls);
        }
    }
}
