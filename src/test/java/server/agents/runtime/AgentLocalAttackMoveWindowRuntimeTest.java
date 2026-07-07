package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentLocalAttackMoveWindowRuntimeTest {
    @Test
    void clearsSettledMoveWindowUsingRuntimeMovementConfig() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentBotCombatCooldownStateRuntime.setMoveWindowMs(entry, 200);

        AgentLocalAttackMoveWindowRuntime.clearActionMoveWindowIfSettled(
                entry,
                new Point(100, 100),
                new Point(100, 100));

        assertFalse(AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry));
    }
}
