package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMushroomKingdomFarmProgressPolicyTest {
    @Test
    void tenthMissSetsCooldownAndExpiredCooldownStartsAFreshCampaign() {
        AgentMushroomKingdomFarmProgress current = new AgentMushroomKingdomFarmProgress(
                1, 27, 9, 2, 1_000L, 0L, 0, 0, 0, 2_000L, "");

        AgentMushroomKingdomFarmProgress capped =
                AgentMushroomKingdomFarmProgressPolicy.recordYetiRun(
                        current, true, 0, 10_000L);

        assertEquals(10, capped.yetiRuns());
        assertEquals(3, capped.relevantBoxOpportunities());
        assertEquals(10_000L + AgentMushroomKingdomFarmProgressRuntime.YETI_COOLDOWN_MS,
                capped.yetiCooldownUntilMs());
        assertSame(capped, AgentMushroomKingdomFarmProgressPolicy.beginYetiCampaign(
                capped, capped.yetiCooldownUntilMs() - 1));

        AgentMushroomKingdomFarmProgress reset =
                AgentMushroomKingdomFarmProgressPolicy.beginYetiCampaign(
                        capped, capped.yetiCooldownUntilMs());
        assertEquals(0, reset.yetiRuns());
        assertEquals(0L, reset.yetiCooldownUntilMs());
    }

    @Test
    void desiredWeaponStopsWithoutCooldownAndScrollOutcomeIsRecorded() {
        AgentMushroomKingdomFarmProgress current =
                AgentMushroomKingdomFarmProgress.empty(27, 1_000L);
        AgentMushroomKingdomFarmProgress weapon =
                AgentMushroomKingdomFarmProgressPolicy.recordYetiRun(
                        current, true, 1472089, 2_000L);
        AgentMushroomKingdomFarmProgress scrolled =
                AgentMushroomKingdomFarmProgressPolicy.recordScrollAttempt(
                        weapon, true, "SUCCESS", 3_000L);

        assertEquals(1472089, weapon.acquiredWeaponItemId());
        assertEquals(0L, weapon.yetiCooldownUntilMs());
        assertEquals(1, scrolled.scrollAttempts());
        assertEquals(1, scrolled.scrollCompletions());
        assertTrue(scrolled.lastStopReason().contains("SUCCESS"));
    }
}
