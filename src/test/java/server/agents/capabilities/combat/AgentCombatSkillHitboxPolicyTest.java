package server.agents.capabilities.combat;

import client.Character;
import constants.skills.Crossbowman;
import constants.skills.Hermit;
import constants.skills.Hunter;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatSkillHitboxPolicyTest {
    @Test
    void identifiesStrikePointAnchoredAoeSkills() {
        assertTrue(AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(Hunter.ARROW_BOMB));
        assertFalse(AgentCombatSkillHitboxPolicy.isStrikePointAnchoredAoeSkill(Crossbowman.IRON_ARROW));
    }

    @Test
    void preservesCloseRangeFallbackGeometry() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(130);

        Rectangle hitBox = AgentCombatSkillHitboxPolicy.fallbackCloseRangeSkillHitBox(effect, agent, null, false);

        assertEquals(new Rectangle(100, 150, 130, 70), hitBox);
    }

    @Test
    void usesMeasuredPierceLineProjectileReach() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        when(agent.getSkills()).thenReturn(java.util.Map.of());
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(100);

        Rectangle ironArrow = AgentCombatSkillHitboxPolicy.fallbackSkillHitBox(
                effect, agent, false, AgentAttackRoute.RANGED, Crossbowman.IRON_ARROW, null);
        Rectangle avenger = AgentCombatSkillHitboxPolicy.fallbackSkillHitBox(
                effect, agent, false, AgentAttackRoute.RANGED, Hermit.AVENGER, null);

        assertEquals(new Rectangle(105, 168, 395, 4), ironArrow);
        assertEquals(new Rectangle(105, 140, 395, 60), avenger);
    }
}
