package client.command;

import client.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatResetCalculatorTest {
    @Test
    void apIncludesLegacyStartingPoolAndAdvancementAwards() {
        assertEquals(9, StatResetCalculator.availableAp(Job.BEGINNER, 1, 0, false));
        assertEquals(0, StatResetCalculator.availableAp(Job.BEGINNER, 1, 0, true));
        assertEquals(154, StatResetCalculator.availableAp(Job.WARRIOR, 30, 0, false));
        assertEquals(149, StatResetCalculator.availableAp(Job.WARRIOR, 30, 0, true));
        assertEquals(359, StatResetCalculator.availableAp(Job.ASSASSIN, 70, 0, false));
        assertEquals(349, StatResetCalculator.availableAp(Job.ASSASSIN, 70, 10, false));
    }

    @Test
    void cygnusApIncludesBonusLevelBands() {
        assertEquals(187, StatResetCalculator.availableAp(Job.DAWNWARRIOR2, 30, 0, false));
        assertEquals(182, StatResetCalculator.availableAp(Job.DAWNWARRIOR2, 30, 0, true));
    }

    @Test
    void normalJobsReceiveOnlyLegallyEarnedSp() {
        assertEquals(0, StatResetCalculator.totalSp(Job.BEGINNER, 30));
        assertEquals(61, StatResetCalculator.totalSp(Job.WARRIOR, 30));
        assertEquals(62, StatResetCalculator.totalSp(Job.ASSASSIN, 30));
        assertEquals(68, StatResetCalculator.totalSp(Job.IL_WIZARD, 30));
    }

    @Test
    void evanSpIsSeparatedBySkillBook() {
        int[] expected = new int[10];
        expected[0] = 33;
        expected[1] = 3;
        assertArrayEquals(expected, StatResetCalculator.availableSp(Job.EVAN2, 20));

        assertArrayEquals(new int[]{33, 33, 33, 33, 33, 63, 63, 63, 123, 3},
                StatResetCalculator.availableSp(Job.EVAN10, 160));
    }
}
