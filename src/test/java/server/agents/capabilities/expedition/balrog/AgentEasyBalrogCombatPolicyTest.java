package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEasyBalrogCombatPolicyTest {
    @Test
    void bodyFormationUsesOnlyTheUpperLeftPlatform() {
        for (int ordinal = 0; ordinal < AgentBalrogDefinition.ROSTER_SIZE; ordinal++) {
            for (boolean ranged : new boolean[]{false, true}) {
                Point anchor = AgentEasyBalrogCombatPolicy.headAnchor(ordinal, ranged);
                assertTrue(anchor.x >= -100 && anchor.x <= 220);
                assertTrue(anchor.y < 0);
            }
        }
    }

    @Test
    void headFormationRequiresAgentsToReturnAfterKnockback() {
        Point anchor = AgentEasyBalrogCombatPolicy.headAnchor(0, false);

        assertTrue(AgentEasyBalrogCombatPolicy.atAnchor(new Point(anchor.x + 10, anchor.y), anchor));
        assertFalse(AgentEasyBalrogCombatPolicy.atAnchor(new Point(anchor.x - 80, anchor.y), anchor));
    }

    @Test
    void rangedHeadStationsStayFartherLeftThanMeleeStations() {
        for (int ordinal = 0; ordinal < AgentBalrogDefinition.ROSTER_SIZE; ordinal++) {
            Point ranged = AgentEasyBalrogCombatPolicy.headAnchor(ordinal, true);
            Point melee = AgentEasyBalrogCombatPolicy.headAnchor(ordinal, false);
            assertTrue(ranged.x < melee.x);
        }
    }

    @Test
    void meleeHeadStationsRemainInOrdinaryCloseRangeOfTheWzBody() {
        int visibleHeadLeftX = 274;
        for (int ordinal = 0; ordinal < AgentBalrogDefinition.ROSTER_SIZE; ordinal++) {
            Point melee = AgentEasyBalrogCombatPolicy.headAnchor(ordinal, false);
            assertTrue(visibleHeadLeftX - melee.x <= 80);
        }
    }
}
