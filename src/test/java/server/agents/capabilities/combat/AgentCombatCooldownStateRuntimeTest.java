package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatCooldownStateRuntimeTest {
    @Test
    void adaptsAttackCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));

        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 600);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 400);

        assertTrue(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));
        assertEquals(600, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));

        AgentCombatCooldownStateRuntime.tickAttackCooldown(entry, value -> value - 100);

        assertEquals(500, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));

        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);

        assertFalse(AgentCombatCooldownStateRuntime.hasAttackCooldown(entry));
    }

    @Test
    void adaptsMoveWindowState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentCombatCooldownStateRuntime.hasMoveWindow(entry));

        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, 250);
        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, 100);

        assertTrue(AgentCombatCooldownStateRuntime.hasMoveWindow(entry));
        assertEquals(250, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
        assertTrue(AgentCombatCooldownStateRuntime.blocksGroundedAttack(entry, false));
        assertFalse(AgentCombatCooldownStateRuntime.blocksGroundedAttack(entry, true));

        AgentCombatCooldownStateRuntime.tickMoveWindow(entry, value -> value - 50);

        assertEquals(200, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void adaptsMobHitCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentCombatCooldownStateRuntime.hasMobHitCooldown(entry));

        AgentCombatCooldownStateRuntime.setMobHitCooldownMs(entry, 1_000);

        assertTrue(AgentCombatCooldownStateRuntime.hasMobHitCooldown(entry));
        assertEquals(1_000, AgentCombatCooldownStateRuntime.mobHitCooldownMs(entry));

        AgentCombatCooldownStateRuntime.tickMobHitCooldown(entry, value -> value - 250);

        assertEquals(750, AgentCombatCooldownStateRuntime.mobHitCooldownMs(entry));
    }

    @Test
    void adaptsAlertResetState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentCombatCooldownStateRuntime.alertResetScheduled(entry));
        assertEquals(0L, AgentCombatCooldownStateRuntime.alertedUntilMs(entry));

        AgentCombatCooldownStateRuntime.setAlertedUntilMs(entry, 12_345L);
        AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, true);

        assertEquals(12_345L, AgentCombatCooldownStateRuntime.alertedUntilMs(entry));
        assertTrue(AgentCombatCooldownStateRuntime.alertResetScheduled(entry));

        AgentCombatCooldownStateRuntime.setAlertResetScheduled(entry, false);

        assertFalse(AgentCombatCooldownStateRuntime.alertResetScheduled(entry));
    }
}
