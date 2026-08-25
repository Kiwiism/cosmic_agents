package server.life;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.GlobalDropFact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalDropLevelEligibilityTest {
    @Test
    void acceptsOnlyMobLevelsInsideInclusiveRange() {
        MonsterGlobalDropEntry entry = new MonsterGlobalDropEntry(
                5220000, 1_000, -1, 1, 1, (short) 0, 50, 90);

        assertFalse(entry.isEligibleForMobLevel(49));
        assertTrue(entry.isEligibleForMobLevel(50));
        assertTrue(entry.isEligibleForMobLevel(90));
        assertFalse(entry.isEligibleForMobLevel(91));
    }

    @Test
    void economyFactUsesTheSameInclusiveEligibilityRule() {
        GlobalDropFact fact = new GlobalDropFact(5220000, 1_000, -1, 1, 1, 0, 90, 255);

        assertFalse(fact.isEligibleForMobLevel(89));
        assertTrue(fact.isEligibleForMobLevel(90));
        assertTrue(fact.isEligibleForMobLevel(255));
        assertThrows(IllegalArgumentException.class,
                () -> new GlobalDropFact(5220000, 1_000, -1, 1, 1, 0, 100, 99));
    }

    @Test
    void stockConstructorRetainsAllLevelBehavior() {
        MonsterGlobalDropEntry entry = new MonsterGlobalDropEntry(
                5220000, 1_000, -1, 1, 1, (short) 0);

        assertTrue(entry.isEligibleForMobLevel(0));
        assertTrue(entry.isEligibleForMobLevel(255));
    }
}
