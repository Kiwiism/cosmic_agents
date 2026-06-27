package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotCombatCooldownStateRuntimeTest {
    @Test
    void adaptsAttackCooldownState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry));

        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, 600);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, 400);

        assertTrue(AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry));
        assertEquals(600, AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry));

        AgentBotCombatCooldownStateRuntime.tickAttackCooldown(entry, value -> value - 100);

        assertEquals(500, AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry));

        AgentBotCombatCooldownStateRuntime.clearAttackCooldown(entry);

        assertFalse(AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry));
    }

    @Test
    void adaptsMoveWindowState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry));

        AgentBotCombatCooldownStateRuntime.maxMoveWindow(entry, 250);
        AgentBotCombatCooldownStateRuntime.maxMoveWindow(entry, 100);

        assertTrue(AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry));
        assertEquals(250, AgentBotCombatCooldownStateRuntime.moveWindowMs(entry));
        assertTrue(AgentBotCombatCooldownStateRuntime.blocksGroundedAttack(entry, false));
        assertFalse(AgentBotCombatCooldownStateRuntime.blocksGroundedAttack(entry, true));

        AgentBotCombatCooldownStateRuntime.tickMoveWindow(entry, value -> value - 50);

        assertEquals(200, AgentBotCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void adaptsMobHitCooldownState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry));

        AgentBotCombatCooldownStateRuntime.setMobHitCooldownMs(entry, 1_000);

        assertTrue(AgentBotCombatCooldownStateRuntime.hasMobHitCooldown(entry));
        assertEquals(1_000, AgentBotCombatCooldownStateRuntime.mobHitCooldownMs(entry));

        AgentBotCombatCooldownStateRuntime.tickMobHitCooldown(entry, value -> value - 250);

        assertEquals(750, AgentBotCombatCooldownStateRuntime.mobHitCooldownMs(entry));
    }
}
