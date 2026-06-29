package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import constants.skills.Archer;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProjectileHitboxTest {
    @Test
    void returnsNullWhenAgentOrPositionMissing() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(null);

        assertNull(AgentProjectileHitbox.clientProjectileHitBox(null, false, 1.0f));
        assertNull(AgentProjectileHitbox.clientProjectileHitBox(agent, false, 1.0f));
    }

    @Test
    void buildsClientProjectileHitboxFromAgentFeet() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        when(agent.getSkills()).thenReturn(Map.of());

        assertEquals(new Rectangle(105, 150, 395, 100),
                AgentProjectileHitbox.clientProjectileHitBox(agent, false, 1.0f));
        assertEquals(new Rectangle(-300, 150, 395, 100),
                AgentProjectileHitbox.clientProjectileHitBox(agent, true, 1.0f));
    }

    @Test
    void addsPassiveRangeSkillsToProjectileRange() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        Skill eyeOfAmazon = new Skill(Archer.EYE_OF_AMAZON);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(120);
        eyeOfAmazon.addLevelEffect(effect);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(eyeOfAmazon, null);
        when(agent.getSkills()).thenReturn(skills);
        when(agent.getSkillLevel(eyeOfAmazon)).thenReturn((byte) 1);

        assertEquals(new Rectangle(105, 150, 515, 100),
                AgentProjectileHitbox.clientProjectileHitBox(agent, false, 1.0f));
    }
}
