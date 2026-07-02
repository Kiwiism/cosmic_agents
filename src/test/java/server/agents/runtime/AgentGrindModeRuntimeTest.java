package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentGrindModeTickService;
import server.bots.BotEntry;

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

class AgentGrindModeRuntimeTest {
    @Test
    void delegatesGrindModeThroughAgentRuntimeHookBundle() {
        BotEntry entry = mock(BotEntry.class);
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

            AgentLiveModeTickRuntime.LocalAttackResult result = AgentGrindModeRuntime.tickGrindMode(
                    entry,
                    agent,
                    agentPosition,
                    targetPosition,
                    true,
                    (moveEntry, moveTarget, runAiTick) -> calls.add("move"),
                    333);

            assertTrue(result.consumedTick());
            assertEquals(updatedTarget, result.targetPos());
            assertEquals(List.of("seek:" + AgentCombatConfigValue.GRIND_SEEK_RANGE + ":loot:333"), calls);
        }
    }

    private static final class AgentCombatConfigValue {
        private static final int GRIND_SEEK_RANGE =
                server.agents.capabilities.combat.AgentCombatConfig.cfg.GRIND_SEEK_RANGE;
    }
}
