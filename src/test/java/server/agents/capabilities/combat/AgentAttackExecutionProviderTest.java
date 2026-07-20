package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Skill;
import client.inventory.WeaponType;
import constants.skills.Magician;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAttackExecutionProviderTest {
    @Test
    void magicCastUsesNormalSixAndOnlyBooster() {
        Skill skill = mock(Skill.class);
        Character agent = mock(Character.class);
        when(skill.getId()).thenReturn(Magician.MAGIC_CLAW);
        when(agent.getBuffedValue(BuffStat.BOOSTER)).thenReturn(-2);
        when(agent.getBuffedValue(BuffStat.SPEED_INFUSION)).thenReturn(-2);

        assertEquals(4, AgentAttackExecutionProvider.resolveSkillEffectiveAttackSpeed(skill, agent));
        assertEquals(6, AgentAttackExecutionProvider.resolveMagicAttackSpeed(null));
    }

    @Test
    void wandMagicFallbackUsesMagicCastActions() {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(Magician.MAGIC_CLAW);

        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(
                null, skill, 1, WeaponType.WAND);

        assertTrue(Set.of("wand1", "wand2").contains(action));
    }
}
