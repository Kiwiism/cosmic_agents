package client;

import constants.skills.Brawler;
import constants.skills.Magician;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HpMpGrowthPolicyTest {
    private static final HpMpGrowthPolicy.Growth STARTING_STATS = new HpMpGrowthPolicy.Growth(50, 5);

    @Test
    void explorerBranchesReachDesignedLevel160Stats() {
        assertEquals(new HpMpGrowthPolicy.Growth(16_376, 2_524),
                explorerAt160(10, Job.WARRIOR, Job.FIGHTER, Job.CRUSADER, Job.HERO, Warrior.IMPROVED_MAXHP, 40));
        assertEquals(new HpMpGrowthPolicy.Growth(4_488, 13_982),
                explorerAt160(8, Job.MAGICIAN, Job.FP_WIZARD, Job.FP_MAGE, Job.FP_ARCHMAGE,
                        Magician.IMPROVED_MAX_MP_INCREASE, 20));
        assertEquals(new HpMpGrowthPolicy.Growth(7_476, 4_429),
                explorerAt160(10, Job.BOWMAN, Job.HUNTER, Job.RANGER, Job.BOWMASTER, 0, 0));
        assertEquals(new HpMpGrowthPolicy.Growth(7_476, 4_429),
                explorerAt160(10, Job.THIEF, Job.ASSASSIN, Job.HERMIT, Job.NIGHTLORD, 0, 0));
        assertEquals(new HpMpGrowthPolicy.Growth(8_356, 3_939),
                explorerAt160(10, Job.THIEF, Job.BANDIT, Job.CHIEFBANDIT, Job.SHADOWER, 0, 0));
        assertEquals(new HpMpGrowthPolicy.Growth(12_026, 4_079),
                explorerAt160(10, Job.PIRATE, Job.BRAWLER, Job.MARAUDER, Job.BUCCANEER,
                        Brawler.IMPROVE_MAX_HP, 30));
        assertEquals(new HpMpGrowthPolicy.Growth(8_236, 5_419),
                explorerAt160(10, Job.PIRATE, Job.GUNSLINGER, Job.OUTLAW, Job.CORSAIR, 0, 0));
    }

    @Test
    void passiveBonusDependsOnCurrentLevelAndSkillRankNotLearnTime() {
        assertEquals(new HpMpGrowthPolicy.Growth(6_000, 0),
                HpMpGrowthPolicy.retroactivePassiveBonus(Warrior.IMPROVED_MAXHP, 40, 160));
        assertEquals(new HpMpGrowthPolicy.Growth(0, 3_040),
                HpMpGrowthPolicy.retroactivePassiveBonus(Magician.IMPROVED_MAX_MP_INCREASE, 20, 160));
        assertEquals(new HpMpGrowthPolicy.Growth(3_900, 0),
                HpMpGrowthPolicy.retroactivePassiveBonus(Brawler.IMPROVE_MAX_HP, 30, 160));
        assertEquals(HpMpGrowthPolicy.Growth.ZERO,
                HpMpGrowthPolicy.retroactivePassiveBonus(Warrior.IMPROVED_MAXHP, 40, 10));
    }

    @Test
    void identifiesBothForbiddenHpMpApEncodings() {
        assertTrue(HpMpGrowthPolicy.isHpMpApStat(2048));
        assertTrue(HpMpGrowthPolicy.isHpMpApStat(8192));
        assertFalse(HpMpGrowthPolicy.isHpMpApStat(64));
    }

    private static HpMpGrowthPolicy.Growth explorerAt160(
            int firstAdvancementLevel,
            Job firstJob,
            Job secondJob,
            Job thirdJob,
            Job fourthJob,
            int passiveSkillId,
            int passiveEffectX) {
        HpMpGrowthPolicy.Growth stats = STARTING_STATS;
        Job currentJob = Job.BEGINNER;
        for (int newLevel = 2; newLevel <= 160; newLevel++) {
            stats = stats.plus(HpMpGrowthPolicy.levelGain(currentJob));
            if (newLevel == firstAdvancementLevel) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, firstJob, newLevel));
                currentJob = firstJob;
            } else if (newLevel == 30) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, secondJob, newLevel));
                currentJob = secondJob;
            } else if (newLevel == 70) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, thirdJob, newLevel));
                currentJob = thirdJob;
            } else if (newLevel == 120) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, fourthJob, newLevel));
                currentJob = fourthJob;
            }
        }
        return stats.plus(HpMpGrowthPolicy.retroactivePassiveBonus(passiveSkillId, passiveEffectX, 160));
    }

    @Test
    void lateExplorerAdvancementReconcilesToOnTimeGrowth() {
        assertEquals(explorerAtLevelWithAdvancements(200, 10, 30, 70, 120,
                        Job.PIRATE, Job.BRAWLER, Job.MARAUDER, Job.BUCCANEER),
                explorerAtLevelWithAdvancements(200, 10, 200, 200, 200,
                        Job.PIRATE, Job.BRAWLER, Job.MARAUDER, Job.BUCCANEER));
        assertEquals(explorerAtLevelWithAdvancements(200, 10, 30, 70, 120,
                        Job.PIRATE, Job.GUNSLINGER, Job.OUTLAW, Job.CORSAIR),
                explorerAtLevelWithAdvancements(200, 200, 200, 200, 200,
                        Job.PIRATE, Job.GUNSLINGER, Job.OUTLAW, Job.CORSAIR));
        assertEquals(explorerAtLevelWithAdvancements(200, 10, 30, 70, 120,
                        Job.THIEF, Job.BANDIT, Job.CHIEFBANDIT, Job.SHADOWER),
                explorerAtLevelWithAdvancements(200, 10, 200, 200, 200,
                        Job.THIEF, Job.BANDIT, Job.CHIEFBANDIT, Job.SHADOWER));
    }

    @Test
    void adminRebuildIncludesEveryTargetAdvancementEvenWhenUnderLevel() {
        assertEquals(new HpMpGrowthPolicy.Growth(3_500, 925),
                HpMpGrowthPolicy.baseForJobAtLevel(Job.HERO, 1));
        assertEquals(new HpMpGrowthPolicy.Growth(10_376, 2_524),
                HpMpGrowthPolicy.baseForJobAtLevel(Job.HERO, 160));
        assertEquals(new HpMpGrowthPolicy.Growth(4_488, 10_942),
                HpMpGrowthPolicy.baseForJobAtLevel(Job.FP_ARCHMAGE, 160));
    }

    private static HpMpGrowthPolicy.Growth explorerAtLevelWithAdvancements(
            int targetLevel, int firstLevel, int secondLevel, int thirdLevel, int fourthLevel,
            Job firstJob, Job secondJob, Job thirdJob, Job fourthJob) {
        HpMpGrowthPolicy.Growth stats = STARTING_STATS;
        Job currentJob = Job.BEGINNER;
        for (int newLevel = 2; newLevel <= targetLevel; newLevel++) {
            stats = stats.plus(HpMpGrowthPolicy.levelGain(currentJob));
            if (newLevel == firstLevel) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, firstJob, newLevel));
                currentJob = firstJob;
            }
            if (newLevel == secondLevel) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, secondJob, newLevel));
                currentJob = secondJob;
            }
            if (newLevel == thirdLevel) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, thirdJob, newLevel));
                currentJob = thirdJob;
            }
            if (newLevel == fourthLevel) {
                stats = stats.plus(HpMpGrowthPolicy.jobAdvancementGain(currentJob, fourthJob, newLevel));
                currentJob = fourthJob;
            }
        }
        return stats;
    }
}
