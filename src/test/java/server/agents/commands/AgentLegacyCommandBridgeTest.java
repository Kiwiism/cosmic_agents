package server.agents.commands;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLegacyCommandBridgeTest {
    @Test
    void combatConfigDelegatesToAgentCombatConfig() {
        assertEquals(AgentCombatConfig.configFieldLines(), AgentLegacyCommandBridge.combatConfigLines());
        assertEquals(
                AgentCombatConfig.configFieldLine("attack_range_x"),
                AgentLegacyCommandBridge.combatConfigLine("attack_range_x"));
    }
}
