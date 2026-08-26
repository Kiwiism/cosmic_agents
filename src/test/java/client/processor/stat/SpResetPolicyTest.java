package client.processor.stat;

import client.Job;
import constants.skills.Fighter;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpResetPolicyTest {
    @Test
    void rejectsSameSkillCrossTierAndForeignBranchTransfers() {
        assertFalse(SpResetPolicy.isValidTransfer(5050001,
                Warrior.IMPROVED_MAXHP, Warrior.IMPROVED_MAXHP, Job.HERO));
        assertFalse(SpResetPolicy.isValidTransfer(5050001,
                Fighter.SWORD_MASTERY, Warrior.IMPROVED_MAXHP, Job.HERO));
        assertFalse(SpResetPolicy.isValidTransfer(5050002,
                Warrior.POWER_STRIKE, Fighter.SWORD_MASTERY, Job.HERO));
        assertFalse(SpResetPolicy.isValidTransfer(5050001,
                2001002, Warrior.IMPROVED_MAXHP, Job.HERO));
    }

    @Test
    void permitsDistinctSkillsInTheMatchingOwnedTier() {
        assertTrue(SpResetPolicy.isValidTransfer(5050001,
                Warrior.POWER_STRIKE, Warrior.IMPROVED_MAXHP, Job.HERO));
        assertTrue(SpResetPolicy.isValidTransfer(5050002,
                Fighter.SWORD_MASTERY, Fighter.RAGE, Job.HERO));
    }

    @Test
    void rejectsRemovingARequiredFifthPrerequisitePoint() {
        assertFalse(SpResetPolicy.preservesHpMpPassivePrerequisites(
                Warrior.IMPROVED_HPREC, 5, 1, 0));
        assertTrue(SpResetPolicy.preservesHpMpPassivePrerequisites(
                Warrior.IMPROVED_HPREC, 6, 1, 0));
        assertTrue(SpResetPolicy.preservesHpMpPassivePrerequisites(
                Warrior.IMPROVED_HPREC, 5, 0, 0));
    }
}
