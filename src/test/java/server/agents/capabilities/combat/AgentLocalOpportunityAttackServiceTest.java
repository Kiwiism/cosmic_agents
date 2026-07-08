package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.supplies.AgentAmmoStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentLocalOpportunityAttackServiceTest {
    @Test
    void returnsWithoutSideEffectsWhenAgentHasNoAmmo() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentAmmoStateRuntime.setNoAmmo(entry, true);
        AtomicInteger hookCalls = new AtomicInteger();

        AgentLocalOpportunityAttackService.Result result =
                AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                        entry,
                        mock(Character.class),
                        new Point(10, 20),
                        new Point(30, 40),
                        new Point(30, 40),
                        true,
                        true,
                        hooksCounting(hookCalls));

        assertFalse(result.consumedTick());
        assertEquals(new Point(30, 40), result.targetPos());
        assertEquals(0, hookCalls.get());
    }

    @Test
    void returnsWithoutSideEffectsWhenAgentOrPositionIsMissing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger hookCalls = new AtomicInteger();

        AgentLocalOpportunityAttackService.Result missingAgent =
                AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                        entry,
                        null,
                        new Point(10, 20),
                        new Point(30, 40),
                        new Point(30, 40),
                        true,
                        true,
                        hooksCounting(hookCalls));
        AgentLocalOpportunityAttackService.Result missingPosition =
                AgentLocalOpportunityAttackService.tryLocalOpportunityAttack(
                        entry,
                        mock(Character.class),
                        null,
                        new Point(50, 60),
                        new Point(50, 60),
                        true,
                        true,
                        hooksCounting(hookCalls));

        assertFalse(missingAgent.consumedTick());
        assertEquals(new Point(30, 40), missingAgent.targetPos());
        assertFalse(missingPosition.consumedTick());
        assertEquals(new Point(50, 60), missingPosition.targetPos());
        assertEquals(0, hookCalls.get());
    }

    private static AgentLocalOpportunityAttackService.Hooks hooksCounting(AtomicInteger hookCalls) {
        return new AgentLocalOpportunityAttackService.Hooks(
                (entry, agentPos, combatTargetPos) -> {
                    hookCalls.incrementAndGet();
                    return combatTargetPos;
                },
                movementProfile -> {
                    hookCalls.incrementAndGet();
                    return 0;
                },
                (entry, agent, dx) -> hookCalls.incrementAndGet(),
                (entry, agentPos, referencePos) -> hookCalls.incrementAndGet());
    }
}
