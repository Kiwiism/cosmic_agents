package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentCombatActionLockRuntime;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCombatActionLockRuntimeTest {
    @Test
    void ticksAttackCooldownBeforeMoveWindow() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 120);
        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, 90);

        AgentCombatActionLockRuntime.tickActionLock(entry);

        assertEquals(70, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));
        assertEquals(90, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void ticksMoveWindowAfterAttackCooldownClears() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, 90);

        AgentCombatActionLockRuntime.tickActionLock(entry);

        assertEquals(40, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }
}
