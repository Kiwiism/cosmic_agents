package server.agents.capabilities.supplies;

import client.Character;
import client.Skill;
import constants.skills.Crusader;
import constants.skills.Magician;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.integration.SkillGateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPassiveRecoveryPolicyTest {
    @Test
    void shouldReturnBaseHpRecoveryWhenNotStandingStill() {
        assertEquals(10, AgentPassiveRecoveryPolicy.hpRecoveryFromBonuses(10, false, 25));
    }

    @Test
    void shouldAddImprovedHpRecoveryBonusWhenStandingStill() {
        assertEquals(35, AgentPassiveRecoveryPolicy.hpRecoveryFromBonuses(10, true, 25));
    }

    @Test
    void shouldReturnBaseMpRecoveryWhenNotStandingStill() {
        assertEquals(3, AgentPassiveRecoveryPolicy.mpRecoveryFromBonuses(3, false, 10, 20, 30, 40));
    }

    @Test
    void shouldAddAllLegacyMpRecoveryBonusesWhenStandingStill() {
        assertEquals(103, AgentPassiveRecoveryPolicy.mpRecoveryFromBonuses(3, true, 10, 20, 30, 40));
    }

    @Test
    void hpRecoveryReadsImprovedHpRecoveryThroughSkillGateway() {
        Character agent = mock(Character.class);
        Skill improvedHp = new Skill(Warrior.IMPROVED_HPREC);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) 25);
        improvedHp.addLevelEffect(effect);
        when(agent.getSkillLevel(improvedHp)).thenReturn((byte) 1);
        SkillGateway skills = mock(SkillGateway.class);
        when(skills.getSkill(Warrior.IMPROVED_HPREC)).thenReturn(improvedHp);

        assertEquals(35, AgentPassiveRecoveryPolicy.hpRecovery(agent, 10, true, skills));
    }

    @Test
    void mpRecoveryReadsFlatAndMagicianBonusesThroughSkillGateway() {
        Character agent = mock(Character.class);
        Skill crusaderRecovery = new Skill(Crusader.IMPROVING_MPREC);
        StatEffect crusaderEffect = mock(StatEffect.class);
        when(crusaderEffect.getMp()).thenReturn((short) 5);
        crusaderRecovery.addLevelEffect(crusaderEffect);
        Skill magicianRecovery = new Skill(Magician.IMPROVED_MP_RECOVERY);
        StatEffect magicianEffect = mock(StatEffect.class);
        magicianRecovery.addLevelEffect(magicianEffect);
        when(agent.getSkillLevel(crusaderRecovery)).thenReturn((byte) 1);
        when(agent.getSkillLevel(magicianRecovery)).thenReturn((byte) 1);
        when(agent.getInt()).thenReturn(80);
        SkillGateway skills = mock(SkillGateway.class);
        when(skills.getSkill(Crusader.IMPROVING_MPREC)).thenReturn(crusaderRecovery);
        when(skills.getSkill(Magician.IMPROVED_MP_RECOVERY)).thenReturn(magicianRecovery);

        assertEquals(16, AgentPassiveRecoveryPolicy.mpRecovery(agent, 3, true, skills));
    }
}
