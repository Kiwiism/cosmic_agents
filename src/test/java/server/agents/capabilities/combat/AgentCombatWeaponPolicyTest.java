package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import constants.skills.Assassin;
import constants.skills.Bandit;
import constants.skills.DragonKnight;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Spearman;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatWeaponPolicyTest {
    @Test
    void somersaultKickRemainsWeaponNeutralForKpqAccuracySpear() {
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.SOMERSAULT_KICK, WeaponType.SPEAR_STAB));
    }

    @Test
    void gatesPirateWeaponSpecificSkillsWithoutRestrictingSomersaultKick() {
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.FLASH_FIST, WeaponType.KNUCKLE));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.FLASH_FIST, WeaponType.SPEAR_STAB));

        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.DOUBLE_SHOT, WeaponType.GUN));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.DOUBLE_SHOT, WeaponType.SPEAR_STAB));

        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.SOMERSAULT_KICK, WeaponType.GUN));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Pirate.SOMERSAULT_KICK, WeaponType.SPEAR_STAB));
    }

    @Test
    void gatesSinDitSkillsToTheCurrentlyEquippedWeapon() {
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Rogue.LUCKY_SEVEN, WeaponType.CLAW));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Assassin.DRAIN, WeaponType.CLAW));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Rogue.LUCKY_SEVEN, WeaponType.DAGGER_THIEVES));

        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Rogue.DOUBLE_STAB, WeaponType.DAGGER_THIEVES));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Bandit.SAVAGE_BLOW, WeaponType.DAGGER_OTHER));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                Rogue.DOUBLE_STAB, WeaponType.CLAW));

        assertTrue(AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                Assassin.CLAW_BOOSTER, WeaponType.CLAW));
        assertFalse(AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                Assassin.CLAW_BOOSTER, WeaponType.DAGGER_THIEVES));
    }

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

        assertTrue(AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                Spearman.SPEAR_BOOSTER, WeaponType.SPEAR_STAB));
        assertFalse(AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                Spearman.SPEAR_BOOSTER, WeaponType.POLE_ARM_SWING));
        assertTrue(AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                Spearman.POLEARM_BOOSTER, WeaponType.POLE_ARM_SWING));
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
