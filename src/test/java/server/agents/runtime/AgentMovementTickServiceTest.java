package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementTickService;
import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMovementTickServiceTest {
    @Test
    void navigationConsumedTickStopsPipeline() {
        AgentRuntimeEntry entry = entry();
        List<String> calls = new ArrayList<>();

        AgentMovementTickService.stepMovementCore(
                entry,
                new Point(10, 20),
                true,
                hooks(calls, true, false));

        assertEquals(List.of("nav"), calls);
    }

    @Test
    void fidgetConsumedTickStopsAfterPreciseMarkerPoint() {
        AgentRuntimeEntry entry = entry();
        List<String> calls = new ArrayList<>();

        AgentMovementTickService.stepMovementCore(
                entry,
                new Point(10, 20),
                true,
                hooks(calls, false, true));

        assertEquals(List.of("nav", "fidget"), calls);
    }

    @Test
    void groundedAiTickRunsMovementCommittedEdgeAndMaintenance() {
        AgentRuntimeEntry entry = entry();
        List<String> calls = new ArrayList<>();

        AgentMovementTickService.stepMovementCore(
                entry,
                new Point(10, 20),
                true,
                hooks(calls, false, false));

        assertEquals(List.of("nav", "fidget", "phase", "edge", "stuck", "cleanup"), calls);
    }

    @Test
    void nonAiTickSkipsCommittedEdgeButRunsMaintenance() {
        AgentRuntimeEntry entry = entry();
        List<String> calls = new ArrayList<>();

        AgentMovementTickService.stepMovementCore(
                entry,
                new Point(10, 20),
                false,
                hooks(calls, false, false));

        assertEquals(List.of("nav", "fidget", "phase", "stuck", "cleanup"), calls);
    }

    @Test
    void airborneAiTickSkipsCommittedEdgeButRunsMaintenance() {
        AgentRuntimeEntry entry = entry();
        AgentMovementStateRuntime.setInAir(entry, true);
        List<String> calls = new ArrayList<>();

        AgentMovementTickService.stepMovementCore(
                entry,
                new Point(10, 20),
                true,
                hooks(calls, false, false));

        assertEquals(List.of("nav", "fidget", "phase", "stuck", "cleanup"), calls);
    }

    private static AgentMovementTickService.MovementTickHooks hooks(List<String> calls,
                                                                    boolean navigationConsumed,
                                                                    boolean fidgetConsumed) {
        return new AgentMovementTickService.MovementTickHooks(
                (entry, targetPosition, runAiTick) -> {
                    calls.add("nav");
                    return new AgentMovementTickService.NavigationResult(navigationConsumed, new Point(30, 40));
                },
                (entry, targetPosition, runAiTick) -> {
                    calls.add("fidget");
                    return fidgetConsumed;
                },
                (entry, targetPosition, runAiTick) -> calls.add("phase"),
                (entry, targetPosition) -> calls.add("edge"),
                entry -> calls.add("stuck"),
                entry -> calls.add("cleanup"));
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }
}
