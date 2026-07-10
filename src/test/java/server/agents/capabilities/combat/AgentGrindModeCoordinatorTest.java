package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentGrindModeCoordinatorTest {
    @Test
    void defaultGrindModeUsesRuntimeLootRadius() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Point agentPosition = new Point(10, 20);
        Point targetPosition = new Point(30, 40);
        Point updatedTarget = new Point(50, 60);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentGrindModeTickService> grindMode = mockStatic(AgentGrindModeTickService.class)) {
            grindMode.when(() -> AgentGrindModeTickService.tickGrindMode(
                            eq(entry),
                            eq(agent),
                            eq(agentPosition),
                            eq(targetPosition),
                            anyBoolean(),
                            any(AgentGrindModeTickService.Hooks.class)))
                    .thenAnswer(invocation -> {
                        AgentGrindModeTickService.Hooks hooks = invocation.getArgument(5);
                        calls.add("loot:" + hooks.lootRadius());
                        return new AgentGrindModeTickService.Result(true, updatedTarget);
                    });

            AgentGrindModeTickService.Result result = AgentGrindModeCoordinator.tickGrindMode(
                    entry,
                    agent,
                    agentPosition,
                    targetPosition,
                    true,
                    (moveEntry, moveTarget, runAiTick) -> calls.add("move"));

            assertTrue(result.consumedTick());
            assertEquals(updatedTarget, result.targetPos());
            assertEquals(List.of("loot:" + AgentRuntimeConfig.cfg.LOOT_RADIUS), calls);
        }
    }

    @Test
    void delegatesGrindModeThroughCombatHookBundle() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Point agentPosition = new Point(10, 20);
        Point targetPosition = new Point(30, 40);
        Point updatedTarget = new Point(50, 60);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentGrindModeTickService> grindMode = mockStatic(AgentGrindModeTickService.class)) {
            grindMode.when(() -> AgentGrindModeTickService.tickGrindMode(
                            eq(entry),
                            eq(agent),
                            eq(agentPosition),
                            eq(targetPosition),
                            anyBoolean(),
                            any(AgentGrindModeTickService.Hooks.class)))
                    .thenAnswer(invocation -> {
                        AgentGrindModeTickService.Hooks hooks = invocation.getArgument(5);
                        calls.add("seek:" + hooks.seekRange() + ":loot:" + hooks.lootRadius());
                        return new AgentGrindModeTickService.Result(true, updatedTarget);
                    });

            AgentGrindModeTickService.Result result = AgentGrindModeCoordinator.tickGrindMode(
                    entry,
                    agent,
                    agentPosition,
                    targetPosition,
                    true,
                    (moveEntry, moveTarget, runAiTick) -> calls.add("move"),
                    333);

            assertTrue(result.consumedTick());
            assertEquals(updatedTarget, result.targetPos());
            assertEquals(List.of("seek:" + AgentCombatConfig.cfg.GRIND_SEEK_RANGE + ":loot:333"), calls);
        }
    }
}
