package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatCooldownStateTest {
    @Test
    void tracksAttackCooldownAndMoveWindow() {
        AgentCombatCooldownState state = new AgentCombatCooldownState();

        assertFalse(state.hasAttackCooldown());
        assertFalse(state.hasMoveWindow());

        state.maxAttackCooldown(600);
        state.maxAttackCooldown(400);
        state.maxMoveWindow(250);
        state.maxMoveWindow(100);

        assertEquals(600, state.attackCooldownMs());
        assertEquals(250, state.moveWindowMs());
        assertTrue(state.blocksGroundedAttack(false));
        assertTrue(state.blocksGroundedAttack(true));

        state.clearAttackCooldown();

        assertFalse(state.hasAttackCooldown());
        assertTrue(state.blocksGroundedAttack(false));
        assertFalse(state.blocksGroundedAttack(true));

        state.tickMoveWindow(value -> value - 50);
        assertEquals(200, state.moveWindowMs());
    }

    @Test
    void tracksMobHitCooldownAndAlertReset() {
        AgentCombatCooldownState state = new AgentCombatCooldownState();

        assertFalse(state.hasMobHitCooldown());
        assertEquals(0L, state.alertedUntilMs());
        assertFalse(state.alertResetScheduled());

        state.setMobHitCooldownMs(1_000);
        state.tickMobHitCooldown(value -> value - 250);
        state.setAlertedUntilMs(12_345L);
        state.setAlertResetScheduled(true);

        assertTrue(state.hasMobHitCooldown());
        assertEquals(750, state.mobHitCooldownMs());
        assertEquals(12_345L, state.alertedUntilMs());
        assertTrue(state.alertResetScheduled());
    }
}
