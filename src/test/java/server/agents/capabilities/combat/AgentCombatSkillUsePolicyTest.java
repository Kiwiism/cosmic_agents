package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.Skill;
import client.SkillFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.StatEffect;

class AgentCombatSkillUsePolicyTest {
    @Test
    void shouldRejectMissingSkillOrNonPositiveLevel() {
        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(1000)).thenReturn(null);

            assertFalse(AgentCombatSkillUsePolicy.canPaySkillCost(mock(Character.class), 1000, 1));
            assertFalse(AgentCombatSkillUsePolicy.canPaySkillCost(mock(Character.class), 1000, 0));
        }
    }

    @Test
    void shouldDelegateAffordabilityToSkillEffect() {
        Character bot = mock(Character.class);
        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(skill.getEffect(3)).thenReturn(effect);
        when(effect.canPaySkillCost(bot)).thenReturn(true);

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(2000)).thenReturn(skill);

            assertTrue(AgentCombatSkillUsePolicy.canPaySkillCost(bot, 2000, 3));
        }
    }
}
