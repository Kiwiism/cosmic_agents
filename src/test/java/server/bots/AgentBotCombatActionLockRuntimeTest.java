package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatActionLockRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotCombatActionLockRuntimeTest {
    @Test
    void ticksAttackCooldownBeforeMoveWindow() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, 120);
        AgentBotCombatCooldownStateRuntime.maxMoveWindow(entry, 90);

        AgentBotCombatActionLockRuntime.tickActionLock(entry);

        assertEquals(70, AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry));
        assertEquals(90, AgentBotCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void ticksMoveWindowAfterAttackCooldownClears() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotCombatCooldownStateRuntime.maxMoveWindow(entry, 90);

        AgentBotCombatActionLockRuntime.tickActionLock(entry);

        assertEquals(40, AgentBotCombatCooldownStateRuntime.moveWindowMs(entry));
    }
}
