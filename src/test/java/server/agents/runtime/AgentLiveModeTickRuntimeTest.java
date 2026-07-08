package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLiveModeTickRuntimeTest {
    @Test
    void shopVisitConsumptionShortCircuitsRemainingLiveModes() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Character followAnchor = mock(Character.class);
        Point agentPos = new Point(10, 20);
        Point targetPos = new Point(30, 40);
        Point consumedTarget = new Point(50, 60);
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentShopVisitTickService> shopVisit = mockStatic(AgentShopVisitTickService.class)) {
            shopVisit.when(() -> AgentShopVisitTickService.tickShopVisitIfPending(
                            any(AgentRuntimeEntry.class),
                            any(Character.class),
                            anyBoolean(),
                            any()))
                    .thenAnswer(invocation -> {
                        calls.add("shop");
                        return new AgentShopVisitTickService.Result(true, consumedTarget);
                    });

            AgentLiveModeTickService.Result result = AgentLiveModeTickRuntime.tickLiveModes(
                    new AgentLiveModeTickService.Context(
                            entry,
                            agent,
                            agentPos,
                            targetPos,
                            null,
                            followAnchor,
                            true,
                            123L),
                    false,
                    (attackEntry, attackAgent, attackAgentPos, attackTargetPos, followTargetPos, allowMoveWindow, updateMoveWindow) -> {
                        calls.add("attack");
                        return new AgentLiveModeTickRuntime.LocalAttackResult(false, attackTargetPos);
                    },
                    (moveEntry, moveTargetPos, moveRunAiTick) -> calls.add("move"),
                    (farmEntry, farmAgent, farmAgentPos, farmRunAiTick) -> calls.add("farm"),
                    (grindEntry, grindAgent, grindAgentPos, grindTargetPos, grindRunAiTick) -> {
                        calls.add("grind");
                        return new AgentLiveModeTickRuntime.LocalAttackResult(false, grindTargetPos);
                    },
                    120);

            assertEquals(targetPos, result.targetPosition());
            assertEquals(List.of("shop"), calls);
        }
    }
}
