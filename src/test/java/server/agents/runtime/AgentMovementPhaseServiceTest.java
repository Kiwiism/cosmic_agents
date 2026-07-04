package server.agents.runtime;

import server.agents.integration.AgentBotClimbStateRuntime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Rope;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentMovementPhaseServiceTest {
    @Test
    void climbingTakesPriorityOverAirAndSwim() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, new Rope(10, 0, 100, false));
        List<String> calls = new ArrayList<>();

        AgentMovementPhaseService.tickMovementPhase(entry, target, true, hooks(calls, true));

        assertEquals(List.of("climb:true"), calls);
    }

    @Test
    void swimmingRunsOnlyWhenInAirOnSwimMap() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        List<String> calls = new ArrayList<>();

        AgentMovementPhaseService.tickMovementPhase(entry, target, false, hooks(calls, true));

        assertEquals(List.of("swim"), calls);
    }

    @Test
    void airborneRunsWhenInAirOutsideSwimMap() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        List<String> calls = new ArrayList<>();

        AgentMovementPhaseService.tickMovementPhase(entry, target, false, hooks(calls, false));

        assertEquals(List.of("air"), calls);
    }

    @Test
    void groundedRunsWhenNotInAirOrClimbing() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);
        List<String> calls = new ArrayList<>();

        AgentMovementPhaseService.tickMovementPhase(entry, target, false, hooks(calls, true));

        assertEquals(List.of("ground"), calls);
    }

    private static AgentMovementPhaseService.MovementPhaseHooks hooks(List<String> calls, boolean swimMap) {
        return new AgentMovementPhaseService.MovementPhaseHooks(
                (entry, target) -> swimMap,
                (entry, target, runAiTick) -> calls.add("climb:" + runAiTick),
                (entry, target) -> calls.add("swim"),
                (entry, target) -> calls.add("air"),
                (entry, target) -> calls.add("ground"));
    }

    private static BotEntry entry() {
        return new BotEntry(mock(Character.class), mock(Character.class), null);
    }
}
