package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqSessionTest {
    @Test
    void ownsLeadershipLeaseAndStageEightOrder() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 17L, 900, 6, 1_000L);
        session.addMember(101, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(104, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(105, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(106, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(101, 102);

        assertTrue(session.claimExecutionTick(102, 1_000L, 3_000L));
        assertFalse(session.claimExecutionTick(103, 2_000L, 3_000L));
        assertEquals(java.util.List.of(1, 3, 6, 7, 4), session.stage8Combination());
        var firstAssignments = session.stage8Assignments(java.util.List.of(101, 102, 103, 104, 105));
        session.advanceStage8(2_001L);
        assertEquals(java.util.List.of(1, 3, 6, 7, 8), session.stage8Combination());
        var secondAssignments = session.stage8Assignments(java.util.List.of(101, 102, 103, 104, 105));
        long movers = firstAssignments.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(secondAssignments.get(entry.getKey()))).count();
        assertEquals(1L, movers);
    }

    @Test
    void reassignsBothJmsMoversAtACombinationRollover() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 17L, 900, 6, 1_000L);
        java.util.List<Integer> participants = java.util.List.of(101, 102, 103, 104, 105);

        for (int attempt = 0; attempt < 4; attempt++) {
            session.stage8Assignments(participants);
            session.advanceStage8(2_000L + attempt);
        }
        var before = session.stage8Assignments(participants);
        assertEquals(java.util.Set.of(1, 3, 6, 7, 9),
                new java.util.HashSet<>(before.values()));

        session.advanceStage8(2_005L);
        var after = session.stage8Assignments(participants);
        assertEquals(java.util.Set.of(1, 3, 6, 4, 8),
                new java.util.HashSet<>(after.values()));
        assertEquals(2L, before.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(after.get(entry.getKey())))
                .count());
    }

    @Test
    void keepsStageEightAssignmentChatSilentUntilEnabled() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 17L, 900, 6, 1_000L);

        assertFalse(session.stage8AssignmentChatEnabled());
        session.setStage8AssignmentChatEnabled(true);
        assertTrue(session.stage8AssignmentChatEnabled());
        assertFalse(session.stage8AssignmentAnnounced());

        session.markStage8AssignmentAnnounced(2_000L);
        assertTrue(session.stage8AssignmentAnnounced());
        session.setStage8AssignmentChatEnabled(false);
        session.setStage8AssignmentChatEnabled(true);
        assertFalse(session.stage8AssignmentAnnounced());
    }

    @Test
    void rejectsFiveMemberSessionForSixMemberRequestOverflow() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 5, 0L);
        for (int id = 1; id <= 5; id++) session.addMember(id, AgentLpqMemberState.MemberType.AGENT);
        assertThrows(IllegalStateException.class,
                () -> session.addMember(6, AgentLpqMemberState.MemberType.AGENT));
    }

    @Test
    void tracksPartyPassStagnationAndResetsForProgressAndStageChanges() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_1, 1_100L);

        assertEquals(0L, session.observePassRecovery(24, 2_000L));
        assertEquals(30_000L, session.observePassRecovery(24, 32_000L));
        session.markPassRecoveryMobSweep(32_000L);
        assertTrue(session.passRecoveryMobSweepAttempted());
        session.markPassRecoveryConsolidation(32_500L);
        assertEquals(500L, session.observePassRecovery(24, 33_000L));
        assertEquals(0L, session.observePassRecovery(25, 33_000L));
        assertFalse(session.passRecoveryMobSweepAttempted());
        session.markPassRecoveryPassesAwarded(33_000L);
        assertTrue(session.passRecoveryPassesAwarded());
        assertEquals(0L, session.observePassRecovery(0, 34_000L));
        assertTrue(session.passRecoveryPassesAwarded());

        session.transition(AgentLpqSession.Phase.STAGE_2, 34_000L);
        assertEquals(0L, session.observePassRecovery(0, 35_000L));
        assertFalse(session.passRecoveryMobSweepAttempted());
        assertFalse(session.passRecoveryPassesAwarded());
    }

    @Test
    void passHandoffRecoveryKeepsItsOwnExitGraceAndResetsOnStageChange() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_5, 1_100L);

        assertFalse(session.passHandoffRecoveryActive());
        assertTrue(session.beginPassHandoffRecovery(2_000L));
        assertTrue(session.passHandoffRecoveryActive());
        assertFalse(session.beginPassHandoffRecovery(2_500L));
        assertEquals(30_000L, session.passHandoffRecoveryElapsed(32_000L));

        session.transition(AgentLpqSession.Phase.STAGE_6, 33_000L);
        assertFalse(session.passHandoffRecoveryActive());
        assertEquals(0L, session.passHandoffRecoveryElapsed(34_000L));
    }

    @Test
    void passHandoffRecoveryRequiresTheFullAuthoredPassTotal() {
        assertTrue(AgentLpqCoordinator.passHandoffRecoveryApplicable(5, 24, 24));
        assertFalse(AgentLpqCoordinator.passHandoffRecoveryApplicable(5, 23, 24));
        assertTrue(AgentLpqCoordinator.passHandoffRecoveryApplicable(7, 3, 3));
        assertFalse(AgentLpqCoordinator.passHandoffRecoveryApplicable(6, 0, 0));
    }

    @Test
    void tracksSubmissionReadinessPerStageAndResetsWhenPassesAreNoLongerReady() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_2, 1_100L);

        assertEquals(0L, session.observeSubmissionReady(true, 2_000L));
        assertEquals(30_000L, session.observeSubmissionReady(true, 32_000L));
        assertEquals(0L, session.observeSubmissionReady(false, 33_000L));
        assertEquals(0L, session.observeSubmissionReady(true, 34_000L));

        session.transition(AgentLpqSession.Phase.STAGE_3, 35_000L);
        assertEquals(0L, session.observeSubmissionReady(true, 36_000L));
    }

    @Test
    void retainsCouponRegroupAcrossDropsAndClearsItOnStageChange() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);

        session.beginCouponRegroup(1, 1_050L);
        assertFalse(session.couponRegrouping(1));

        session.transition(AgentLpqSession.Phase.STAGE_1, 1_100L);
        session.beginCouponRegroup(1, 1_200L);
        assertTrue(session.couponRegrouping(1));

        session.transition(AgentLpqSession.Phase.STAGE_2, 1_300L);
        assertFalse(session.couponRegrouping(1));
        session.beginCouponRegroup(2, 1_400L);
        assertTrue(session.couponRegrouping(2));

        session.transition(AgentLpqSession.Phase.STAGE_3, 1_500L);
        assertFalse(session.couponRegrouping(2));
        session.beginCouponRegroup(3, 1_600L);
        assertTrue(session.couponRegrouping(3));
    }

    @Test
    void bonusMustRemainDrainedBeforeThePartyLeaves() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.BONUS, 2_000L);

        assertEquals(0L, session.observeBonusDrained(true, 3_000L));
        assertEquals(1_500L, session.observeBonusDrained(true, 4_500L));
        assertEquals(0L, session.observeBonusDrained(false, 4_600L));
        assertEquals(0L, session.observeBonusDrained(true, 5_000L));
        assertEquals(2_000L, session.observeBonusDrained(true, 7_000L));

        session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, 7_100L);
        session.transition(AgentLpqSession.Phase.BONUS, 7_200L);
        assertEquals(0L, session.observeBonusDrained(true, 8_000L));
    }

    @Test
    void recordsTheVisibleStageTwoScoutAndTrapSignals() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);

        assertFalse(session.stage2ScoutPlanAnnounced());
        assertFalse(session.stage2TrapClearAnnounced());
        session.markStage2ScoutPlanAnnounced(2_000L);
        session.markStage2TrapClearAnnounced(3_000L);

        assertTrue(session.stage2ScoutPlanAnnounced());
        assertTrue(session.stage2TrapClearAnnounced());
        assertEquals(3_000L, session.lastProgressAtMs());
    }

    @Test
    void stageSevenLeaderSweepStartsOnlyAfterCombatAndResetsWithThePhase() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_7, 2_000L);

        assertEquals(0L, session.observeStage7CombatCleared(true, 3_000L));
        assertEquals(15_000L, session.observeStage7CombatCleared(true, 18_000L));
        session.advanceStage7LootSweep(18_000L);
        session.markStage7ForceLootAttempted(18_001L);
        assertEquals(1, session.stage7LootSweepIndex());
        assertTrue(session.stage7ForceLootAttempted());

        session.transition(AgentLpqSession.Phase.STAGE_8, 19_000L);
        assertEquals(0, session.stage7LootSweepIndex());
        assertFalse(session.stage7ForceLootAttempted());
    }
}
