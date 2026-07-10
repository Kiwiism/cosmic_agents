package server.agents.runtime;

import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.agents.capabilities.movement.AgentFormationService;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentTickCoreRuntimeTest {
    @Test
    void compactTickCoreEntryOwnsDefaultAgentRuntimeHooks() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentTickCoreService> tickCore = mockStatic(AgentTickCoreService.class)) {
            tickCore.when(() -> AgentTickCoreService.tickCore(
                            eq(entry),
                            eq(7),
                            eq(9),
                            any(AgentTickCoreService.Hooks.class)))
                    .thenAnswer(invocation -> {
                        calls.add("tickCore");
                        return null;
                    });

            AgentTickCoreRuntime.tickCore(
                    entry,
                    7,
                    9,
                    tickEntry -> calls.add("grind"),
                    tickEntry -> calls.add("follow"));

            org.junit.jupiter.api.Assertions.assertEquals(List.of("tickCore"), calls);
        }
    }

    @Test
    void defaultTickCoreUsesAgentRuntimeConfigAndHookBundle() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentTickCoreService> tickCore = mockStatic(AgentTickCoreService.class)) {
            tickCore.when(() -> AgentTickCoreService.tickCore(
                            eq(entry),
                            eq(7),
                            eq(9),
                            any(AgentTickCoreService.Hooks.class)))
                    .thenAnswer(invocation -> {
                        calls.add("tickCore");
                        return null;
                    });

            AgentTickCoreRuntime.tickCore(
                    entry,
                    7,
                    9,
                    (tickEntry, leaderId) -> mock(Character.class),
                    (tickEntry, agent, leader, nowMs, leaderId) -> false,
                    (tickEntry, agent) -> false,
                    (tickEntry, agent, runAiTick) -> calls.add("standalone"),
                    (tickEntry, agent, leader) -> false,
                    (tickEntry, leader) -> mock(Character.class),
                    tickEntry -> snapshot(),
                    tickEntry -> calls.add("script"),
                    tickEntry -> calls.add("grind"),
                    tickEntry -> calls.add("follow"),
                    (attackEntry, attackAgent, attackAgentPos, attackTargetPos, attackFollowTargetPos, allowMoveWindow, updateMoveWindow) ->
                            new AgentLiveModeTickRuntime.LocalAttackResult(false, attackTargetPos),
                    (moveEntry, moveTargetPos, moveRunAiTick) -> calls.add("move"),
                    (farmEntry, farmAgent, farmAgentPos, farmRunAiTick) -> calls.add("farm"),
                    (grindEntry, grindAgent, grindAgentPos, grindTargetPos, grindRunAiTick) ->
                            new AgentLiveModeTickRuntime.LocalAttackResult(false, grindTargetPos));

            org.junit.jupiter.api.Assertions.assertEquals(List.of("tickCore"), calls);
        }
    }

    @Test
    void delegatesTickCoreThroughAgentRuntimeHookBundle() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentTickCoreService> tickCore = mockStatic(AgentTickCoreService.class)) {
            tickCore.when(() -> AgentTickCoreService.tickCore(
                            eq(entry),
                            eq(7),
                            eq(9),
                            any(AgentTickCoreService.Hooks.class)))
                    .thenAnswer(invocation -> {
                        calls.add("tickCore");
                        return null;
                    });

            AgentTickCoreRuntime.tickCore(
                    entry,
                    7,
                    9,
                    (tickEntry, leaderId) -> mock(Character.class),
                    (tickEntry, agent, leader, nowMs, leaderId) -> false,
                    (tickEntry, agent) -> false,
                    (tickEntry, agent, runAiTick) -> calls.add("standalone"),
                    (tickEntry, agent, leader) -> false,
                    (tickEntry, leader) -> mock(Character.class),
                    tickEntry -> snapshot(),
                    tickEntry -> calls.add("script"),
                    tickEntry -> calls.add("grind"),
                    tickEntry -> calls.add("follow"),
                    (attackEntry, attackAgent, attackAgentPos, attackTargetPos, attackFollowTargetPos, allowMoveWindow, updateMoveWindow) ->
                            new AgentLiveModeTickRuntime.LocalAttackResult(false, attackTargetPos),
                    (moveEntry, moveTargetPos, moveRunAiTick) -> calls.add("move"),
                    (farmEntry, farmAgent, farmAgentPos, farmRunAiTick) -> calls.add("farm"),
                    (grindEntry, grindAgent, grindAgentPos, grindTargetPos, grindRunAiTick) ->
                            new AgentLiveModeTickRuntime.LocalAttackResult(false, grindTargetPos),
                    650,
                    1200,
                    2,
                    120);

            org.junit.jupiter.api.Assertions.assertEquals(List.of("tickCore"), calls);
        }
    }

    private static AgentTargetSnapshot snapshot() {
        return new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 0, 0),
                new Point(0, 0),
                new Point(0, 0),
                "leader",
                new Point(0, 0),
                new Point(0, 0),
                null,
                null,
                null,
                new Point(0, 0),
                "test");
    }
}
