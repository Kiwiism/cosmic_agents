package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLiveModeTickServiceTest {
    @Test
    void seatedAgentSkipsLiveMovementModes() {
        Character agent = mock(Character.class);
        when(agent.getChair()).thenReturn(3010000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        Point target = new Point(30, 40);
        List<String> calls = new ArrayList<>();

        AgentLiveModeTickService.Result result = AgentLiveModeTickService.tickLiveModes(
                new AgentLiveModeTickService.Context(
                        entry, agent, new Point(10, 20), target, target, null, true, 123L),
                new AgentLiveModeTickService.Hooks(
                        (shopEntry, shopAgent, runAiTick) -> {
                            calls.add("shop");
                            return AgentLiveModeTickService.PhaseResult.fallThrough(null);
                        },
                        (followEntry, followAgent, agentPosition, targetPosition, followTarget, anchor, runAiTick) ->
                                AgentLiveModeTickService.PhaseResult.fallThrough(targetPosition),
                        (idleEntry, idleAgent, targetPosition, nowMs) -> false,
                        (scriptEntry, scriptAgent, agentPosition, targetPosition, runAiTick) ->
                                AgentLiveModeTickService.PhaseResult.fallThrough(targetPosition),
                        (farmEntry, farmAgent, agentPosition, runAiTick) -> false,
                        (grindEntry, grindAgent, agentPosition, targetPosition, runAiTick) ->
                                AgentLiveModeTickService.PhaseResult.fallThrough(targetPosition),
                        (moveEntry, targetPosition, runAiTick) -> calls.add("final")));

        assertEquals(List.of(), calls);
        assertEquals(target, result.targetPosition());
    }

    @Test
    void preservesLiveModeOrderingAndTargetPropagation() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character followAnchor = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        Point agentPosition = new Point(10, 20);
        Point initialTarget = new Point(30, 40);
        Point shopTarget = new Point(50, 60);
        Point followTarget = new Point(70, 80);
        Point grindTarget = new Point(90, 100);
        List<String> calls = new ArrayList<>();

        AgentLiveModeTickService.Result result = AgentLiveModeTickService.tickLiveModes(
                new AgentLiveModeTickService.Context(
                        entry,
                        agent,
                        agentPosition,
                        initialTarget,
                        followTarget,
                        followAnchor,
                        true,
                        123L),
                new AgentLiveModeTickService.Hooks(
                        (shopEntry, shopAgent, runAiTick) -> {
                            calls.add("shop");
                            return AgentLiveModeTickService.PhaseResult.fallThrough(shopTarget);
                        },
                        (followEntry, followAgent, followAgentPosition, targetPosition, followTargetPosition, anchor, runAiTick) -> {
                            calls.add("follow:" + targetPosition.x);
                            return AgentLiveModeTickService.PhaseResult.fallThrough(followTargetPosition);
                        },
                        (idleEntry, idleAgent, targetPosition, nowMs) -> {
                            calls.add("followIdle:" + targetPosition.x);
                            return false;
                        },
                        (scriptEntry, scriptAgent, scriptAgentPosition, targetPosition, runAiTick) -> {
                            calls.add("script:" + targetPosition.x);
                            return AgentLiveModeTickService.PhaseResult.fallThrough(new Point(1, 1));
                        },
                        (farmEntry, farmAgent, farmAgentPosition, runAiTick) -> {
                            calls.add("farm");
                            return false;
                        },
                        (grindEntry, grindAgent, grindAgentPosition, targetPosition, runAiTick) -> {
                            calls.add("grind:" + targetPosition.x);
                            return AgentLiveModeTickService.PhaseResult.fallThrough(grindTarget);
                        },
                        (moveEntry, targetPosition, runAiTick) -> calls.add("final:" + targetPosition.x)));

        assertEquals(List.of(
                "shop",
                "follow:50",
                "followIdle:70",
                "script:70",
                "farm",
                "grind:70",
                "final:90"), calls);
        assertEquals(grindTarget, result.targetPosition());
    }

    @Test
    void stopsBeforeLaterPhasesWhenFollowOpportunityConsumesTick() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        Point initialTarget = new Point(30, 40);
        Point consumedTarget = new Point(70, 80);
        List<String> calls = new ArrayList<>();

        AgentLiveModeTickService.Result result = AgentLiveModeTickService.tickLiveModes(
                new AgentLiveModeTickService.Context(
                        entry,
                        agent,
                        new Point(10, 20),
                        initialTarget,
                        new Point(50, 60),
                        leader,
                        true,
                        123L),
                new AgentLiveModeTickService.Hooks(
                        (shopEntry, shopAgent, runAiTick) -> {
                            calls.add("shop");
                            return AgentLiveModeTickService.PhaseResult.fallThrough(null);
                        },
                        (followEntry, followAgent, followAgentPosition, targetPosition, followTargetPosition, anchor, runAiTick) -> {
                            calls.add("follow");
                            return new AgentLiveModeTickService.PhaseResult(true, consumedTarget);
                        },
                        (idleEntry, idleAgent, targetPosition, nowMs) -> {
                            calls.add("followIdle");
                            return false;
                        },
                        (scriptEntry, scriptAgent, scriptAgentPosition, targetPosition, runAiTick) -> {
                            calls.add("script");
                            return AgentLiveModeTickService.PhaseResult.fallThrough(targetPosition);
                        },
                        (farmEntry, farmAgent, farmAgentPosition, runAiTick) -> {
                            calls.add("farm");
                            return false;
                        },
                        (grindEntry, grindAgent, grindAgentPosition, targetPosition, runAiTick) -> {
                            calls.add("grind");
                            return AgentLiveModeTickService.PhaseResult.fallThrough(targetPosition);
                        },
                        (moveEntry, targetPosition, runAiTick) -> calls.add("final")));

        assertEquals(List.of("shop", "follow"), calls);
        assertEquals(consumedTarget, result.targetPosition());
    }
}
