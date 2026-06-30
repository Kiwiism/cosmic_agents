package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.WeaponType;
import constants.skills.Fighter;
import constants.skills.Pirate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWeaponCompatibilityPolicyTest {
    @Test
    void bowmanAcceptsBowAndCrossbowOnly() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.BOWMAN);

        assertTrue(AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, WeaponType.BOW));
        assertTrue(AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, WeaponType.CROSSBOW));
        assertFalse(AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, WeaponType.CLAW));
    }

    @Test
    void pirateSkillsChooseGunOrKnuckle() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.PIRATE);
        when(agent.getSkillLevel(Pirate.DOUBLE_SHOT)).thenReturn(1);
        when(agent.getSkillLevel(Pirate.FLASH_FIST)).thenReturn(0);
        when(agent.getSkillLevel(Pirate.SOMERSAULT_KICK)).thenReturn(0);

        assertTrue(AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, WeaponType.GUN));
        assertFalse(AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, WeaponType.KNUCKLE));
    }

    @Test
    void warriorSkillFamilyControlsTrackKey() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        when(agent.getSkillLevel(Fighter.SWORD_MASTERY)).thenReturn(1);
        when(agent.getSkillLevel(Fighter.SWORD_BOOSTER)).thenReturn(0);
        when(agent.getSkillLevel(Fighter.AXE_MASTERY)).thenReturn(0);
        when(agent.getSkillLevel(Fighter.AXE_BOOSTER)).thenReturn(0);

        assertEquals("sword", AgentWeaponCompatibilityPolicy.weaponUsefulnessTrackKey(agent, WeaponType.SWORD1H));
        assertNull(AgentWeaponCompatibilityPolicy.weaponUsefulnessTrackKey(agent, WeaponType.GENERAL1H_SWING));
    }

    @Test
    void mageJobsAreGrouped() {
        assertTrue(AgentWeaponCompatibilityPolicy.isMageJob(Job.MAGICIAN));
        assertTrue(AgentWeaponCompatibilityPolicy.isMageJob(Job.BISHOP));
        assertFalse(AgentWeaponCompatibilityPolicy.isMageJob(Job.ASSASSIN));
    }
}
