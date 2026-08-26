package client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobProgressionPolicyTest {
    @Test
    void permitsOnlyDirectLevelEligibleExplorerAdvancements() {
        assertTrue(JobProgressionPolicy.isLegalAdvancement(Job.BEGINNER, Job.MAGICIAN, 8));
        assertTrue(JobProgressionPolicy.isLegalAdvancement(Job.PIRATE, Job.BRAWLER, 200));
        assertTrue(JobProgressionPolicy.isLegalAdvancement(Job.BRAWLER, Job.MARAUDER, 200));
        assertFalse(JobProgressionPolicy.isLegalAdvancement(Job.BEGINNER, Job.WARRIOR, 9));
        assertFalse(JobProgressionPolicy.isLegalAdvancement(Job.WARRIOR, Job.WARRIOR, 200));
        assertFalse(JobProgressionPolicy.isLegalAdvancement(Job.WARRIOR, Job.HERO, 200));
        assertFalse(JobProgressionPolicy.isLegalAdvancement(Job.WARRIOR, Job.MAGICIAN, 200));
        assertFalse(JobProgressionPolicy.isLegalAdvancement(Job.NOBLESSE, Job.DAWNWARRIOR1, 200));
    }

    @Test
    void definesHardClassLevelCaps() {
        assertEquals(200, JobProgressionPolicy.classLevelCap(Job.HERO, false));
        assertEquals(120, JobProgressionPolicy.classLevelCap(Job.DAWNWARRIOR1, false));
        assertEquals(255, JobProgressionPolicy.classLevelCap(Job.HERO, true));
    }
}
