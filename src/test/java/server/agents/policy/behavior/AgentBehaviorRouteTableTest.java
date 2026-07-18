package server.agents.policy.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentBehaviorRouteTableTest {
    @Test
    void defaultsToLegacyAndSupportsShadowComparison() {
        AgentBehaviorRouteTable table = new AgentBehaviorRouteTable();
        assertEquals(AgentBehaviorMode.LEGACY,
                table.resolve(AgentBehaviorCapability.COMBAT).mode());

        table.assign(new AgentBehaviorRoute(AgentBehaviorCapability.COMBAT,
                AgentBehaviorMode.SHADOW_COMPARE, "legacy-v1", "combat-v2"));
        assertEquals("combat-v2", table.resolve(AgentBehaviorCapability.COMBAT).shadowVersion());
    }

    @Test
    void shadowModeRequiresAComparisonVersion() {
        assertThrows(IllegalArgumentException.class, () -> new AgentBehaviorRoute(
                AgentBehaviorCapability.COMBAT, AgentBehaviorMode.SHADOW_COMPARE, "legacy-v1", ""));
    }
}
