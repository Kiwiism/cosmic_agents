package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.supplies.AgentAmmoStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void meleeRouteBlockerCanBeApproachedByJumpBeforeAHitboxPlanExists() {
        Point agentPos = new Point(493, 2120);
        Point blockerPos = new Point(535, 2045);

        assertTrue(AgentLocalOpportunityAttackService.shouldJumpTowardUnplannableCloseTarget(
                true, false, WeaponType.SWORD1H, AgentMovementProfile.base(),
                agentPos, blockerPos, 100));
        assertFalse(AgentLocalOpportunityAttackService.shouldJumpTowardUnplannableCloseTarget(
                true, false, WeaponType.GUN, AgentMovementProfile.base(),
                agentPos, blockerPos, 100));
        assertFalse(AgentLocalOpportunityAttackService.shouldJumpTowardUnplannableCloseTarget(
                true, true, WeaponType.SWORD1H, AgentMovementProfile.base(),
                agentPos, blockerPos, 100));
    }

    @Test
    void onlyCommittedTransactionsAuthorizeLocalAttackSideEffects() {
        assertFalse(AgentLocalOpportunityAttackService.committed(null));
        assertFalse(AgentLocalOpportunityAttackService.committed(
                AgentAttackTransactionResult.deferred(
                        AgentAttackTransactionResult.Reason.ATTACK_COOLDOWN, 100, 0)));
        assertFalse(AgentLocalOpportunityAttackService.committed(
                AgentAttackTransactionResult.rejected(
                        AgentAttackTransactionResult.Reason.HANDLER_REJECTED, 100, 0)));
        assertTrue(AgentLocalOpportunityAttackService.committed(
                AgentAttackTransactionResult.committed(
                        100, 0, java.util.List.of(1), 1, 0, 10L)));
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
                (entry, agentPos, referencePos) -> hookCalls.incrementAndGet(),
                (entry, agent, attackPlan) -> {
                    hookCalls.incrementAndGet();
                    return AgentAttackTransactionResult.deferred(
                            AgentAttackTransactionResult.Reason.ATTACK_COOLDOWN, 0, 0);
                });
    }
}
