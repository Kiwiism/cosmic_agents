package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.Job;
import client.Skill;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.integration.SkillGateway;

class AgentCombatSkillUsePolicyTest {
    @Test
    void shouldRejectMissingSkillOrNonPositiveLevel() {
        SkillGateway skills = mock(SkillGateway.class);
        when(skills.getSkill(1000)).thenReturn(null);

        assertFalse(AgentCombatSkillUsePolicy.canPaySkillCost(mock(Character.class), 1000, 1, skills));
        assertFalse(AgentCombatSkillUsePolicy.canPaySkillCost(mock(Character.class), 1000, 0, skills));
    }

    @Test
    void shouldDelegateAffordabilityToSkillEffect() {
        Character bot = mock(Character.class);
        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        SkillGateway skills = mock(SkillGateway.class);
        int skillId = 1001004;
        when(skills.getSkill(skillId)).thenReturn(skill);
        when(skill.getId()).thenReturn(skillId);
        when(skill.getEffect(3)).thenReturn(effect);
        when(bot.getSkillLevel(skill)).thenReturn((byte) 3);
        when(bot.getJob()).thenReturn(Job.HERO);
        when(effect.canPaySkillCost(bot)).thenReturn(true);

        assertTrue(AgentCombatSkillUsePolicy.canPaySkillCost(bot, skillId, 3, skills));
    }
}
