package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import constants.skills.DragonKnight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatWeaponPolicyTest {
    @Test
    void gatesDragonKnightSkillsToMatchingWeaponFamilies() {
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.SPEAR_CRUSHER, WeaponType.SPEAR_STAB));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.SPEAR_DRAGON_FURY, WeaponType.SPEAR_SWING));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_CRUSHER, WeaponType.SPEAR_STAB));

        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_CRUSHER, WeaponType.POLE_ARM_SWING));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_DRAGON_FURY, WeaponType.POLE_ARM_STAB));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.SPEAR_CRUSHER, WeaponType.POLE_ARM_SWING));
    }

    @Test
    void resolvesDragonKnightForcedDamageWeaponTypes() {
        assertEquals(WeaponType.SPEAR_STAB,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(DragonKnight.SPEAR_CRUSHER, null, null));
        assertEquals(WeaponType.POLE_ARM_STAB,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(DragonKnight.POLE_ARM_CRUSHER, null, null));
        assertEquals(WeaponType.SPEAR_SWING,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(DragonKnight.SPEAR_DRAGON_FURY, null, null));
        assertEquals(WeaponType.POLE_ARM_SWING,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(DragonKnight.POLE_ARM_DRAGON_FURY, null, null));
    }

    @Test
    void resolvesStabAndSwingDamageWeaponTypesFromActionName() {
        assertEquals(WeaponType.SPEAR_STAB,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.SPEAR_SWING, "stabT1"));
        assertEquals(WeaponType.POLE_ARM_SWING,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.POLE_ARM_STAB, "swingP1"));
        assertEquals(WeaponType.GENERAL1H_STAB,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.GENERAL1H_SWING, "stabO1"));
        assertEquals(WeaponType.GENERAL2H_SWING,
                AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.GENERAL2H_STAB, "swingO1"));

        assertNull(AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.CLAW, "swingO1"));
        assertNull(AgentCombatWeaponPolicy.damageWeaponTypeForAction(0, WeaponType.SPEAR_STAB, "shoot1"));
    }
}
