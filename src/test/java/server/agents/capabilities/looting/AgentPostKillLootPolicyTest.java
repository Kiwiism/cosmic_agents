package server.agents.capabilities.looting;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPostKillLootPolicyTest {
    @Test
    void meleeDropSettleDelayUsesCurrentOneSecondDefault() {
        assertEquals(1_000L, AgentLootCollectionPolicyConfig.meleeRecentKillTargetAgeMs());
    }

    @Test
    void meleeCollectsAfterFirstKill() {
        assertTrue(AgentPostKillLootPolicy.shouldCollect(
                WeaponType.DAGGER_THIEVES,
                new AgentPostKillLootState.Snapshot(Set.of(1), 1, 1_000L),
                true,
                1_100L));
    }

    @Test
    void rangedBatchesUntilKillOrTimeThreshold() {
        int threshold = AgentLootCollectionPolicyConfig.rangedBatchKills();
        long now = 10_000L;

        assertEquals(5, threshold);

        assertFalse(AgentPostKillLootPolicy.shouldCollect(
                WeaponType.BOW,
                new AgentPostKillLootState.Snapshot(Set.of(1), threshold - 1, now - 100L),
                true,
                now));
        assertTrue(AgentPostKillLootPolicy.shouldCollect(
                WeaponType.BOW,
                new AgentPostKillLootState.Snapshot(Set.of(1), threshold, now - 100L),
                true,
                now));
        assertTrue(AgentPostKillLootPolicy.shouldCollect(
                WeaponType.BOW,
                new AgentPostKillLootState.Snapshot(Set.of(1), 1,
                        now - AgentLootCollectionPolicyConfig.rangedBatchMaxWaitMs()),
                true,
                now));
    }

    @Test
    void noCombatTargetAllowsCollectionWithoutRecentKill() {
        assertTrue(AgentPostKillLootPolicy.shouldCollect(
                WeaponType.GUN,
                new AgentPostKillLootState.Snapshot(Set.of(), 0, 0L),
                false,
                10_000L));
    }

    @Test
    void onlyRecentMeleeDropsUseTheShortTargetAge() {
        assertEquals(
                AgentLootCollectionPolicyConfig.meleeRecentKillTargetAgeMs(),
                AgentPostKillLootPolicy.targetLootAgeMs(WeaponType.DAGGER_THIEVES, true));
        assertEquals(
                AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS,
                AgentPostKillLootPolicy.targetLootAgeMs(WeaponType.DAGGER_THIEVES, false));
        assertEquals(
                AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS,
                AgentPostKillLootPolicy.targetLootAgeMs(WeaponType.BOW, true));
    }
}
