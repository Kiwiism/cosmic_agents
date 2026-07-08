package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCombatFacingRuntimeTest {
    @Test
    void attackPacketStanceUpdatesFacingDirection() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentCombatFacingRuntime.rememberAttackFacing(entry, AgentAttackExecutionProvider.attackPacketStance(true));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));

        AgentCombatFacingRuntime.rememberAttackFacing(entry, AgentAttackExecutionProvider.attackPacketStance(false));
        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
    }
}
