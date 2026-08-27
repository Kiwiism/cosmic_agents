package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.progression.AgentMushroomKingdomFarmSnapshot;
import server.agents.progression.AgentPepeEquipmentSnapshot;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldMushroomKingdomProposalTest {
    private final AgentWorldBaselineProposalProvider provider =
            new AgentWorldBaselineProposalProvider();

    @Test
    void offersStoryAfterSecondJobWithoutMakingItAPlanSuccessor() {
        AgentWorldContext context = context(Set.of(), AgentPepeEquipmentSnapshot.NONE,
                AgentMushroomKingdomFarmSnapshot.EMPTY, 1_000L);

        var intents = provider.propose(context, AgentWorldMilestoneEvaluator.evaluate(context));

        assertTrue(eligible(intents, "mushroom-kingdom-questline"));
        assertFalse(eligible(intents, "mushroom-kingdom-yeti-farm"));
        assertFalse(eligible(intents, "mushroom-kingdom-pepe-scroll"));
    }

    @Test
    void offersYetiFarmOnlyWhenStoryIsCompleteAndWeaponIsMissing() {
        AgentWorldContext context = context(Set.of(2336), AgentPepeEquipmentSnapshot.NONE,
                AgentMushroomKingdomFarmSnapshot.EMPTY, 1_000L);

        var intents = provider.propose(context, AgentWorldMilestoneEvaluator.evaluate(context));

        assertFalse(eligible(intents, "mushroom-kingdom-questline"));
        assertTrue(eligible(intents, "mushroom-kingdom-yeti-farm"));
    }

    @Test
    void offersScrollForTheExactOwnedWeaponAndHonorsYetiCooldown() {
        AgentPepeEquipmentSnapshot weapon = new AgentPepeEquipmentSnapshot(
                1472089, "CLAW", true, false, 7, 2044711, 1);
        AgentWorldContext context = context(Set.of(2336), weapon,
                new AgentMushroomKingdomFarmSnapshot(10, 2, 10_000L, 0, 0, "cooldown"),
                1_000L);

        var intents = provider.propose(context, AgentWorldMilestoneEvaluator.evaluate(context));

        assertFalse(eligible(intents, "mushroom-kingdom-yeti-farm"));
        assertTrue(eligible(intents, "mushroom-kingdom-pepe-scroll"));
    }

    @Test
    void stopsOfferingAllMushroomPlansAtLevelThirtyNine() {
        AgentWorldContext context = context(Set.of(2336), AgentPepeEquipmentSnapshot.NONE,
                AgentMushroomKingdomFarmSnapshot.EMPTY, 1_000L, 39);

        var intents = provider.propose(context, AgentWorldMilestoneEvaluator.evaluate(context));

        assertFalse(intents.stream().anyMatch(intent ->
                intent.requestId().startsWith("mushroom-kingdom")));
    }

    @Test
    void expiredTenRunCooldownMakesANewYetiCampaignSelectable() {
        AgentWorldContext context = context(Set.of(2336), AgentPepeEquipmentSnapshot.NONE,
                new AgentMushroomKingdomFarmSnapshot(10, 2, 999L, 0, 0, "expired"),
                1_000L);

        var intents = provider.propose(context, AgentWorldMilestoneEvaluator.evaluate(context));

        assertTrue(eligible(intents, "mushroom-kingdom-yeti-farm"));
    }

    private static boolean eligible(java.util.List<AgentWorldActivityIntent> intents, String id) {
        return intents.stream().anyMatch(intent -> intent.requestId().equals(id)
                && intent.proposal().eligible());
    }

    private static AgentWorldContext context(
            Set<Integer> completed, AgentPepeEquipmentSnapshot equipment,
            AgentMushroomKingdomFarmSnapshot farming, long nowMs) {
        return context(completed, equipment, farming, nowMs, 35);
    }

    private static AgentWorldContext context(
            Set<Integer> completed, AgentPepeEquipmentSnapshot equipment,
            AgentMushroomKingdomFarmSnapshot farming, long nowMs, int level) {
        return new AgentWorldContext(1L, nowMs, 27, "KiwiAgent", level, 410,
                106_020_000, 100, 100, 50, 50, 1_000L, true, false,
                Set.of(), completed, null, "", "", "", "COMPLETE",
                equipment, farming, Map.of("captureMode", "test"));
    }
}
