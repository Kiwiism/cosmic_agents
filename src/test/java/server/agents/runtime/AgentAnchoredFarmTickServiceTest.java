package server.agents.runtime;

import server.agents.capabilities.combat.AgentAnchoredFarmTickService;
import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAnchoredFarmTickServiceTest {
    @Test
    void mapMismatchClearsAnchorAndIdles() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(200);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(90, 100),
                true,
                hooks(calls, false));

        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(List.of("idle"), calls);
    }

    @Test
    void consumedOpportunityAttackStopsTick() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(100);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(90, 100),
                true,
                hooks(calls, true));

        assertEquals(List.of("attack"), calls);
    }

    @Test
    void alreadyAtAnchorClearsMoveTargetAndGroundIdles() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(100);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(300, 300));
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(104, 100),
                false,
                hooks(calls, false));

        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry));
        assertEquals(List.of("groundIdle"), calls);
    }

    @Test
    void outsideAnchorSetsPreciseTargetAndRunsMovementCore() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(100);
        Point anchor = new Point(100, 100);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, anchor, 100);
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(40, 100),
                false,
                hooks(calls, false));

        assertEquals(anchor, AgentMoveTargetStateRuntime.moveTarget(entry));
        assertEquals(List.of("move:100,100:false"), calls);
    }

    private static AgentAnchoredFarmTickService.AnchoredFarmHooks hooks(List<String> calls,
                                                                       boolean attackConsumed) {
        return new AgentAnchoredFarmTickService.AnchoredFarmHooks(
                (entry, agent, agentPosition, movementTargetPosition, moveWindowReferencePosition,
                 allowCombatMovement, allowJumpTowardTarget) -> {
                    calls.add("attack");
                    return new AgentAnchoredFarmTickService.LocalOpportunityResult(
                            attackConsumed, movementTargetPosition);
                },
                (entry, agent) -> calls.add("idle"),
                (entry, agent) -> calls.add("groundIdle"),
                (entry, targetPosition, runAiTick) ->
                        calls.add("move:" + targetPosition.x + "," + targetPosition.y + ":" + runAiTick));
    }

    private static Character agentOnMap(int mapId) {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(mapId);
        return agent;
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }
}
