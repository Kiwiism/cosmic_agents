package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;

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
        BotEntry entry = entry();
        Character agent = agentOnMap(200);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(90, 100),
                true,
                hooks(calls, false));

        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(List.of("idle"), calls);
    }

    @Test
    void consumedOpportunityAttackStopsTick() {
        BotEntry entry = entry();
        Character agent = agentOnMap(100);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
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
        BotEntry entry = entry();
        Character agent = agentOnMap(100);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(100, 100), 100);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(300, 300));
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(104, 100),
                false,
                hooks(calls, false));

        assertNull(AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertEquals(List.of("groundIdle"), calls);
    }

    @Test
    void outsideAnchorSetsPreciseTargetAndRunsMovementCore() {
        BotEntry entry = entry();
        Character agent = agentOnMap(100);
        Point anchor = new Point(100, 100);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, anchor, 100);
        List<String> calls = new ArrayList<>();

        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                new Point(40, 100),
                false,
                hooks(calls, false));

        assertEquals(anchor, AgentBotMoveTargetStateRuntime.moveTarget(entry));
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

    private static BotEntry entry() {
        return new BotEntry(mock(Character.class), mock(Character.class), null);
    }
}
