package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLocalOpportunityAttackCoordinatorTest {
    @Test
    void delegatesThroughCombatHooksAndReturnsCapabilityResult() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Point agentPosition = new Point(10, 20);
        Point movementTarget = new Point(30, 40);
        Point reference = new Point(50, 60);
        Point updatedTarget = new Point(70, 80);

        try (MockedStatic<AgentLocalOpportunityAttackService> service = mockStatic(AgentLocalOpportunityAttackService.class)) {
            service.when(() -> AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                            eq(entry),
                            eq(agent),
                            eq(agentPosition),
                            eq(movementTarget),
                            eq(reference),
                            anyBoolean(),
                            anyBoolean(),
                            any(AgentLocalOpportunityAttackService.Hooks.class)))
                    .thenReturn(new AgentLocalOpportunityAttackService.Result(true, updatedTarget));

            AgentLocalOpportunityAttackService.Result result =
                    AgentLocalOpportunityAttackCoordinator.tryLocalOpportunityAttack(
                            entry,
                            agent,
                            agentPosition,
                            movementTarget,
                            reference,
                            true,
                            true);

            assertTrue(result.consumedTick());
            assertEquals(updatedTarget, result.targetPos());
        }
    }
}
