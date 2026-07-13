package client;

import org.junit.jupiter.api.Test;
import server.StatEffect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillEligibilityPolicyTest {
    @Test
    void rejectsLearnedSkillOutsideAttachedJobTree() {
        Character character = mock(Character.class);
        Skill skill = skill(2001004, false, character);
        when(character.getJob()).thenReturn(Job.WARRIOR);

        SkillEligibilityPolicy.Result result = SkillEligibilityPolicy.evaluate(
                character, skill, 1, false, () -> true);

        assertFalse(result.allowed());
        assertEquals(SkillEligibilityPolicy.Rejection.WRONG_JOB_TREE, result.rejection());
    }

    @Test
    void allowsLearnedBeginnerSkillForAdvancedJob() {
        Character character = mock(Character.class);
        Skill skill = skill(1002, true, character);
        when(character.getJob()).thenReturn(Job.HERO);

        SkillEligibilityPolicy.Result result = SkillEligibilityPolicy.evaluate(
                character, skill, 1, false, () -> true);

        assertTrue(result.allowed());
    }

    @Test
    void rechecksCooldownAndEquipmentAtExecution() {
        Character character = mock(Character.class);
        Skill skill = skill(1001004, false, character);
        when(character.getJob()).thenReturn(Job.HERO);
        when(character.skillIsCooling(skill.getId())).thenReturn(true);

        SkillEligibilityPolicy.Result cooling = SkillEligibilityPolicy.evaluate(
                character, skill, 1, false, () -> true);
        when(character.skillIsCooling(skill.getId())).thenReturn(false);
        SkillEligibilityPolicy.Result equipment = SkillEligibilityPolicy.evaluate(
                character, skill, 1, false, () -> false);

        assertEquals(SkillEligibilityPolicy.Rejection.COOLDOWN, cooling.rejection());
        assertEquals(SkillEligibilityPolicy.Rejection.WEAPON_OR_AMMO, equipment.rejection());
    }

    @Test
    void rejectsSessionBorrowedSkillEvenWhenItIsLoadedAtTheClaimedLevel() {
        Character character = mock(Character.class);
        Skill skill = skill(4111002, false, character);
        when(character.getJob()).thenReturn(Job.HERMIT);
        when(character.isPartnerSessionBorrowedSkill(4111002)).thenReturn(true);

        SkillEligibilityPolicy.Result result = SkillEligibilityPolicy.evaluate(
                character, skill, 1, false, () -> true);

        assertFalse(result.allowed());
        assertEquals(SkillEligibilityPolicy.Rejection.PARTNER_BORROWED_SKILL, result.rejection());
        assertFalse(SkillEligibilityPolicy.isLearnedAndAllowedJob(character, skill));
    }

    private static Skill skill(int id, boolean beginner, Character character) {
        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(skill.getId()).thenReturn(id);
        when(skill.isBeginnerSkill()).thenReturn(beginner);
        when(character.getSkillLevel(skill)).thenReturn((byte) 1);
        when(character.isGM()).thenReturn(false);
        when(character.getMapId()).thenReturn(100000000);
        when(skill.getEffect(1)).thenReturn(effect);
        when(effect.canPaySkillCost(character)).thenReturn(true);
        return skill;
    }
}
