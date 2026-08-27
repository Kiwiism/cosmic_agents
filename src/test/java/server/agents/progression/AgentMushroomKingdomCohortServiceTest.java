package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterCatalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMushroomKingdomCohortServiceTest {
    @Test
    void rosterCoversEveryExplorerSecondJobExactlyOnce() {
        var roster = AgentMushroomKingdomCohortService.roster();
        assertEquals(12, roster.size());
        assertEquals(AgentSecondJobCatalog.all().keySet(),
                roster.stream().map(AgentMushroomKingdomCohortService.CohortMember::branchId)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(12, roster.stream().map(AgentMushroomKingdomCohortService.CohortMember::name)
                .collect(java.util.stream.Collectors.toSet()).size());
        assertTrue(roster.stream().allMatch(member -> member.name().length() <= 12));
    }

    @Test
    void alternatingAppearanceOrdinalsProvideAnEqualGenderSplit() {
        Set<Integer> genders = new HashSet<>();
        int male = 0;
        int female = 0;
        for (int ordinal = 0; ordinal < AgentMushroomKingdomCohortService.roster().size(); ordinal++) {
            int gender = MapleIslandCohortCharacterCatalog.template(ordinal).gender();
            genders.add(gender);
            if (gender == 0) male++;
            if (gender == 1) female++;
        }
        assertEquals(Set.of(0, 1), genders);
        assertEquals(6, male);
        assertEquals(6, female);
    }

    @Test
    void acceleratedObservationRequiresThirtyCohortDropsAndOneFromTheAgent() {
        var fifty = AgentMushroomKingdomCatalog.require(2312);
        var twoHundred = AgentMushroomKingdomCatalog.require(2328);
        var oneOffKey = AgentMushroomKingdomCatalog.require(2326);
        var yetiBosses = AgentMushroomKingdomCatalog.require(2330);

        assertEquals(0, AgentMushroomKingdomCohortService.accelerationTopUp(fifty, 1, 29));
        assertEquals(0, AgentMushroomKingdomCohortService.accelerationTopUp(fifty, 0, 30));
        assertEquals(49, AgentMushroomKingdomCohortService.accelerationTopUp(fifty, 1, 30));
        assertEquals(199, AgentMushroomKingdomCohortService.accelerationTopUp(twoHundred, 1, 30));
        assertEquals(0, AgentMushroomKingdomCohortService.accelerationTopUp(twoHundred, 200, 30));
        assertEquals(0, AgentMushroomKingdomCohortService.accelerationTopUp(oneOffKey, 1, 30));
        assertEquals(0, AgentMushroomKingdomCohortService.accelerationTopUp(yetiBosses, 1, 30));
    }

    @Test
    void onlyDurablePostBossQuestStatesProvePrimeMinisterAdvancement() {
        int none = client.QuestStatus.Status.NOT_STARTED.getId();
        int started = client.QuestStatus.Status.STARTED.getId();
        int completed = client.QuestStatus.Status.COMPLETED.getId();

        assertFalse(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(none, none, none, none));
        assertFalse(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(none, none, started, none),
                "q2331 starts before the boss and is not post-boss evidence");
        assertTrue(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(completed, none, started, none));
        assertTrue(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(none, started, started, none));
        assertTrue(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(none, none, completed, none));
        assertTrue(AgentMushroomKingdomCohortService
                .hasDurablePostPrimeMinisterEvidence(none, none, started, started));
    }

    @Test
    void tenPercentRequirementsRoundUpAndKeepOneOffsAtOne() {
        assertEquals(0, AgentMushroomKingdomCohortService.tenPercentRequirement(0));
        assertEquals(1, AgentMushroomKingdomCohortService.tenPercentRequirement(1));
        assertEquals(1, AgentMushroomKingdomCohortService.tenPercentRequirement(9));
        assertEquals(1, AgentMushroomKingdomCohortService.tenPercentRequirement(10));
        assertEquals(2, AgentMushroomKingdomCohortService.tenPercentRequirement(11));
        assertEquals(5, AgentMushroomKingdomCohortService.tenPercentRequirement(50));
        assertEquals(10, AgentMushroomKingdomCohortService.tenPercentRequirement(100));
        assertEquals(20, AgentMushroomKingdomCohortService.tenPercentRequirement(200));
    }

    @Test
    void catchUpBonusUsesOnlyPositiveDemonstratedGainAndOnlyForShortenedObjectives() {
        var baseline = new AgentMushroomKingdomCohortService.ObjectiveBaseline(10_000, 500);
        var reached = new AgentMushroomKingdomCohortService.ObjectiveBaseline(10_250, 530);

        assertEquals(new AgentMushroomKingdomCohortService.PendingBonus(2_250, 270),
                AgentMushroomKingdomCohortService.pendingBonus(baseline, reached, 50, 5));
        assertEquals(new AgentMushroomKingdomCohortService.PendingBonus(0, 0),
                AgentMushroomKingdomCohortService.pendingBonus(reached, baseline, 50, 5));
        assertEquals(new AgentMushroomKingdomCohortService.PendingBonus(0, 0),
                AgentMushroomKingdomCohortService.pendingBonus(baseline, reached, 1, 1));
    }

    @Test
    void includeSelfTokenIsExplicitAndOrderIndependent() {
        assertFalse(AgentMushroomKingdomCohortService.includeSelf(null));
        assertFalse(AgentMushroomKingdomCohortService.includeSelf(
                new String[]{"start", "ten-percent", "123"}));
        assertTrue(AgentMushroomKingdomCohortService.includeSelf(
                new String[]{"start", "include-self", "ten-percent", "123"}));
    }

    @Test
    void controlledParticipantMustBeFreshLevelThirtyExplorerSecondJob() {
        int fresh = QuestStatus.Status.NOT_STARTED.getId();
        int started = QuestStatus.Status.STARTED.getId();

        assertNull(AgentMushroomKingdomCohortService
                .controlledParticipantValidation(30, Job.BANDIT.getId(), fresh, false));
        assertNotNull(AgentMushroomKingdomCohortService
                .controlledParticipantValidation(31, Job.BANDIT.getId(), fresh, false));
        assertNotNull(AgentMushroomKingdomCohortService
                .controlledParticipantValidation(30, Job.THIEF.getId(), fresh, false));
        assertNotNull(AgentMushroomKingdomCohortService
                .controlledParticipantValidation(30, Job.BANDIT.getId(), started, false));
        assertNotNull(AgentMushroomKingdomCohortService
                .controlledParticipantValidation(30, Job.BANDIT.getId(), fresh, true));
    }

    @Test
    void resetCoversTheCurrentEntryMainStoryRecoveryAndThornRecords() {
        var resetIds = AgentMushroomKingdomCohortService.resetQuestIdsForJob(Job.BANDIT.getId());

        assertEquals(1 + AgentMushroomKingdomCatalog.mainline().size() + 4, resetIds.size());
        assertEquals(2302, resetIds.getFirst());
        assertTrue(resetIds.containsAll(AgentMushroomKingdomCatalog.mainline().stream()
                .map(AgentMushroomKingdomCatalog.QuestNode::questId).toList()));
        assertTrue(resetIds.containsAll(Set.of(2337, 2338, 2342, 30_000)));
        assertEquals(resetIds.size(), resetIds.stream().distinct().count());
    }

    @Test
    void controlledCharacterFillSuppliesExactMissingItemCount() {
        Character player = mock(Character.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Map<Integer, Integer> statuses = frontierStatuses(2312);
        when(player.getJob()).thenReturn(Job.FIGHTER);
        when(player.getQuestStatus(anyInt())).thenAnswer(invocation ->
                statuses.getOrDefault(invocation.getArgument(0), 0).byteValue());
        when(player.getItemQuantity(4_000_499, false)).thenReturn(1);
        when(inventory.addItem(player, 4_000_499, (short) 49)).thenReturn(true);

        var lines = AgentMushroomKingdomCohortService
                .fillControlledCharacterCondition(player, inventory);

        verify(inventory).addItem(player, 4_000_499, (short) 49);
        assertTrue(lines.getFirst().contains("1->50"));
    }

    @Test
    void controlledCharacterFillSetsOneCreditForEveryColoredYeti() {
        Character player = mock(Character.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Map<Integer, Integer> statuses = frontierStatuses(2330);
        when(player.getJob()).thenReturn(Job.FIGHTER);
        when(player.getQuestStatus(anyInt())).thenAnswer(invocation ->
                statuses.getOrDefault(invocation.getArgument(0), 0).byteValue());

        AgentMushroomKingdomCohortService.fillControlledCharacterCondition(player, inventory);

        verify(player).setQuestProgress(2330, 3_300_005, "001");
        verify(player).setQuestProgress(2330, 3_300_006, "001");
        verify(player).setQuestProgress(2330, 3_300_007, "001");
        verify(inventory, never()).addItem(player, 4_001_318, (short) 1);
    }

    @Test
    void controlledCharacterFillDoesNotSkipAheadToConcurrentRoyalSealQuest() {
        Character player = mock(Character.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Map<Integer, Integer> statuses = frontierStatuses(2332);
        statuses.put(2331, QuestStatus.Status.STARTED.getId());
        when(player.getJob()).thenReturn(Job.FIGHTER);
        when(player.getQuestStatus(anyInt())).thenAnswer(invocation ->
                statuses.getOrDefault(invocation.getArgument(0), 0).byteValue());
        when(player.getItemQuantity(4_032_388, false)).thenReturn(0);
        when(inventory.addItem(player, 4_032_388, (short) 1)).thenReturn(true);

        AgentMushroomKingdomCohortService.fillControlledCharacterCondition(player, inventory);

        verify(inventory).addItem(player, 4_032_388, (short) 1);
        verify(inventory, never()).addItem(player, 4_001_318, (short) 1);
    }

    private static Map<Integer, Integer> frontierStatuses(int activeQuestId) {
        Map<Integer, Integer> statuses = new HashMap<>();
        statuses.put(2300, QuestStatus.Status.COMPLETED.getId());
        for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
            if (node.questId() == activeQuestId) {
                statuses.put(node.questId(), QuestStatus.Status.STARTED.getId());
                break;
            }
            statuses.put(node.questId(), QuestStatus.Status.COMPLETED.getId());
        }
        return statuses;
    }
}
