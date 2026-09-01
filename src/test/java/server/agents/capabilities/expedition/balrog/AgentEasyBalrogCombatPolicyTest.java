package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEasyBalrogCombatPolicyTest {
    @Test
    void clawFormationStaysInTheAuthoredCenterGap() {
        for (int ordinal = 0; ordinal < AgentBalrogDefinition.ROSTER_SIZE; ordinal++) {
            Point anchor = AgentEasyBalrogCombatPolicy.clawAnchor(ordinal);
            assertTrue(anchor.x >= 360 && anchor.x <= 580);
            assertTrue(anchor.y == 258);
        }
    }

    @Test
    void bodyFormationUsesOnlyTheUpperLeftPlatform() {
        for (int ordinal = 0; ordinal < AgentBalrogDefinition.ROSTER_SIZE; ordinal++) {
            Point anchor = AgentEasyBalrogCombatPolicy.headAnchor(ordinal);
            assertTrue(anchor.x >= 10 && anchor.x <= 220);
            assertTrue(anchor.y < 0);
        }
    }

    @Test
    void formationRequiresAgentsToReturnAfterKnockback() {
        Point anchor = AgentEasyBalrogCombatPolicy.clawAnchor(0);

        assertTrue(AgentEasyBalrogCombatPolicy.atAnchor(new Point(anchor.x + 10, anchor.y), anchor));
        assertFalse(AgentEasyBalrogCombatPolicy.atAnchor(new Point(anchor.x - 80, anchor.y), anchor));
    }
}
