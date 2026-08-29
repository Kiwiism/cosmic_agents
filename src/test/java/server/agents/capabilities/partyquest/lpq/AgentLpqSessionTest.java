package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqSessionTest {
    @Test
    void phaseTransitionClearsStaleStageAssignmentsAtomically() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 17L, 900, 6, 1_000L);
        session.addMember(71_001, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(71_002, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(71_001, 71_001);
        session.member(71_002).assign(AgentLpqMemberState.Role.PLATFORM_HOLDER, 922_010_800);
        session.member(71_002).assignPlatform(7);

        session.transition(AgentLpqSession.Phase.STAGE_9, 1_100L);

        assertTrue(session.stageAssignmentsNeedRecalculation());
        assertEquals(AgentLpqMemberState.Role.EVENT_LEADER, session.member(71_001).role());
        assertEquals(AgentLpqMemberState.Role.GENERAL, session.member(71_002).role());
        assertEquals(0, session.member(71_002).assignedMapId());
        assertEquals(0, session.member(71_002).assignedPlatform());

        session.markStageAssignmentsRecalculated(1_101L);
        assertFalse(session.stageAssignmentsNeedRecalculation());
        session.transition(AgentLpqSession.Phase.BONUS, 1_200L);
        assertFalse(session.stageAssignmentsNeedRecalculation());
        session.transition(AgentLpqSession.Phase.STAGE_1, 1_300L);
        assertTrue(session.stageAssignmentsNeedRecalculation());
    }

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
    void combatRecoveryUsesNewLowHpRatherThanMonsterRegenerationAsProgress() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_4, 1_100L);

        assertEquals(0L, session.observePassRecoveryProgress(2, 4, 3, 20_000L, 2_000L));
        assertEquals(30_000L,
                session.observePassRecoveryProgress(2, 4, 3, 21_000L, 32_000L));
        assertEquals(0L,
                session.observePassRecoveryProgress(2, 4, 3, 19_500L, 33_000L));
        assertEquals(10_000L,
                session.observePassRecoveryProgress(2, 4, 3, 20_500L, 43_000L));
        assertEquals(0L,
                session.observePassRecoveryProgress(2, 4, 2, 20_500L, 43_500L));
        assertEquals(0L,
                session.observePassRecoveryProgress(3, 3, 2, 14_000L, 44_000L));

        session.transition(AgentLpqSession.Phase.STAGE_5, 45_000L);
        assertEquals(0L,
                session.observePassRecoveryProgress(0, 0, 0, 0L, 46_000L));
    }

    @Test
    void stageOneCombatCeilingCannotBeExtendedBySparseChipDamage() {
        assertFalse(AgentLpqCoordinator.stageOneCombatCeilingReached(
                1, 1_000L, 360_999L, 360_000L));
        assertTrue(AgentLpqCoordinator.stageOneCombatCeilingReached(
                1, 1_000L, 361_000L, 360_000L));
        assertFalse(AgentLpqCoordinator.stageOneCombatCeilingReached(
                2, 1_000L, 900_000L, 360_000L));
        assertFalse(AgentLpqCoordinator.stageOneCombatCeilingReached(
                1, 1_000L, 900_000L, 0L));
    }

    @Test
    void balloonRallyPlacementUsesOnlyThePartyReturnGrace() {
        assertFalse(AgentLpqCoordinator.balloonRallyRecoveryDue(1, 14_999L));
        assertTrue(AgentLpqCoordinator.balloonRallyRecoveryDue(1, 15_000L));
        assertFalse(AgentLpqCoordinator.balloonRallyRecoveryDue(2, 14_999L));
        assertTrue(AgentLpqCoordinator.balloonRallyRecoveryDue(2, 15_000L));
        assertFalse(AgentLpqCoordinator.balloonRallyRecoveryDue(5, 14_999L));
        assertTrue(AgentLpqCoordinator.balloonRallyRecoveryDue(5, 15_000L));
        assertFalse(AgentLpqCoordinator.balloonRallyForceTransferDue(1, 29_999L));
        assertTrue(AgentLpqCoordinator.balloonRallyForceTransferDue(1, 30_000L));
        assertFalse(AgentLpqCoordinator.balloonRallyForceTransferDue(5, 29_999L));
        assertTrue(AgentLpqCoordinator.balloonRallyForceTransferDue(5, 30_000L));
    }

    @Test
    void everyPostClearTransitionUsesTheStageLocalAbsoluteCap() {
        for (int stage = 2; stage <= 9; stage++) {
            assertFalse(AgentLpqCoordinator.postClearTransitionRecoveryDue(stage, 14_999L));
            assertTrue(AgentLpqCoordinator.postClearTransitionRecoveryDue(stage, 15_000L));
        }
    }

    @Test
    void prematurePhaseCatchUpUsesTheSameCapExceptForStageTwoScouting() {
        assertFalse(AgentLpqCoordinator.stageTransitionCatchUpRecoveryDue(2, 15_000L));
        for (int stage = 3; stage <= 9; stage++) {
            assertFalse(AgentLpqCoordinator.stageTransitionCatchUpRecoveryDue(stage, 14_999L));
            assertTrue(AgentLpqCoordinator.stageTransitionCatchUpRecoveryDue(stage, 15_000L));
        }
    }

    @Test
    void postClearTransitionClockIsPartyLevelAndResetsOnStageChange() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_4, 1_100L);

        assertEquals(0L, session.beginOrObservePostClearTransition(2_000L));
        assertEquals(15_000L, session.beginOrObservePostClearTransition(17_000L));

        session.transition(AgentLpqSession.Phase.STAGE_5, 18_000L);
        assertEquals(0L, session.beginOrObservePostClearTransition(19_000L));
    }

    @Test
    void destinationApproachRetriesAStaleRouteWithoutResettingItsHardClock() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertEquals(0L, member.observeDestinationApproachProgress(
                4, 922_010_400, null, 100_000L, 1_000L));
        assertFalse(member.claimDestinationApproachRetry(9_999L, 10_999L, 10_000L));
        assertTrue(member.claimDestinationApproachRetry(10_000L, 11_000L, 10_000L));
        assertFalse(member.claimDestinationApproachRetry(15_000L, 16_000L, 10_000L));
        assertTrue(member.claimDestinationApproachRetry(20_000L, 21_000L, 10_000L));
        assertEquals(20_000L, member.observeDestinationApproachProgress(
                4, 922_010_400, null, 100_000L, 21_000L));
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
    void passHandoffRecoveryGetsItsOwnNaturalExitThenPortalThenMapFallback() {
        assertEquals(AgentLpqCoordinator.PassHandoffExitAction.NAVIGATE,
                AgentLpqCoordinator.passHandoffExitAction(5, 14_999L));
        assertEquals(AgentLpqCoordinator.PassHandoffExitAction.PLACE_AT_PORTAL,
                AgentLpqCoordinator.passHandoffExitAction(5, 15_000L));
        assertEquals(AgentLpqCoordinator.PassHandoffExitAction.MOVE_TO_STAGE,
                AgentLpqCoordinator.passHandoffExitAction(5, 30_000L));
    }

    @Test
    void stageFourKeepsItsGroundedExitOverlayUntilTheHardPortalDeadline() {
        assertEquals(AgentLpqCoordinator.PassHandoffExitAction.NAVIGATE,
                AgentLpqCoordinator.passHandoffExitAction(4, 15_000L));
        assertEquals(AgentLpqCoordinator.PassHandoffExitAction.MOVE_TO_STAGE,
                AgentLpqCoordinator.passHandoffExitAction(4, 30_000L));
        assertFalse(AgentLpqCoordinator.roomExitPortalPlacementDue(
                4, 29_999L, 29_999L));
        assertTrue(AgentLpqCoordinator.roomExitPortalPlacementDue(
                4, 30_000L, 30_000L));
        assertTrue(AgentLpqCoordinator.roomExitPortalPlacementDue(
                5, 15_000L, 15_000L));
    }

    @Test
    void recordsOneDiagnosticDurationForEachSplitRoomVisit() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                930, AgentLpqMemberState.MemberType.AGENT);

        assertTrue(member.beginRoomTiming(922_010_502, 10_000L));
        assertFalse(member.beginRoomTiming(922_010_502, 11_000L));
        assertEquals(239_999L, member.roomTimingElapsed(922_010_502, 249_999L));
        assertFalse(AgentLpqCoordinator.stageFiveRoomCeilingRecoveryDue(239_999L));
        assertTrue(AgentLpqCoordinator.stageFiveRoomCeilingRecoveryDue(240_000L));
        assertFalse(member.stageFiveCeilingRecoveryAppliedFor(922_010_502));
        member.markStageFiveCeilingRecoveryApplied(922_010_502);
        assertTrue(member.stageFiveCeilingRecoveryAppliedFor(922_010_502));
        assertEquals(240_000L, member.finishRoomTiming(922_010_502, 250_000L));
        assertFalse(member.stageFiveCeilingRecoveryAppliedFor(922_010_502));
        assertEquals(-1L, member.finishRoomTiming(922_010_502, 251_000L));
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
    void loosePassRecoveryWaitsForStableCountAndResetsOnCollection() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_4, 1_100L);

        assertEquals(0L, session.observeLoosePasses(2, 2_000L));
        assertEquals(30_000L, session.observeLoosePasses(2, 32_000L));
        assertEquals(0L, session.observeLoosePasses(1, 33_000L));
        assertEquals(10_000L, session.observeLoosePasses(1, 43_000L));
        assertEquals(0L, session.observeLoosePasses(0, 44_000L));
        assertEquals(0L, session.observeLoosePasses(1, 45_000L));
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
    void balloonRallyRecoveryIsAcceptedOnlyForItsStage() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertFalse(member.balloonRallyRecoveredFor(1));
        member.markBalloonRallyRecovered(1);
        assertTrue(member.balloonRallyRecoveredFor(1));
        assertFalse(member.balloonRallyRecoveredFor(2));
        assertThrows(IllegalArgumentException.class,
                () -> member.markBalloonRallyRecovered(0));
    }

    @Test
    void balloonRecoveryClockStartsOnlyAfterEveryoneReturnsAndResetsIfAnyoneLeaves() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);

        assertEquals(0L, session.observeMainMapRally(922_010_500, false, 10_000L));
        assertEquals(0L, session.observeMainMapRally(922_010_500, true, 40_000L));
        assertEquals(15_000L, session.observeMainMapRally(922_010_500, true, 55_000L));

        assertEquals(0L, session.observeMainMapRally(922_010_500, false, 56_000L));
        assertEquals(0L, session.observeMainMapRally(922_010_500, true, 70_000L));
        assertEquals(1_000L, session.observeMainMapRally(922_010_500, true, 71_000L));

        session.transition(AgentLpqSession.Phase.STAGE_6, 72_000L);
        assertEquals(0L, session.observeMainMapRally(922_010_500, true, 90_000L));
        assertEquals(0L, session.observeMainMapRally(922_010_600, true, 91_000L));
    }

    @Test
    void stageFiveUnobservedAssistPausesWhileObservedAndResetsAfterPassPickup() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertEquals(0L, member.observeStageFiveAssist(922_010_503, 1, false, 1_000L));
        assertEquals(10_000L, member.observeStageFiveAssist(922_010_503, 1, false, 11_000L));
        assertEquals(10_000L, member.observeStageFiveAssist(922_010_503, 1, true, 31_000L));
        assertEquals(15_000L, member.observeStageFiveAssist(922_010_503, 1, false, 36_000L));

        member.markStageFiveAssistApplied();
        assertTrue(member.stageFiveAssistApplied());
        assertEquals(0L, member.observeStageFiveAssist(922_010_503, 2, false, 40_000L));
        assertFalse(member.stageFiveAssistApplied());
    }

    @Test
    void splitCollectionStagesRetainOneRegroupStateUntilSubmission() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);

        session.beginCouponRegroup(5, 3_000L);

        assertTrue(session.couponRegrouping(5));
        assertFalse(session.couponRegrouping(4));
        session.beginCouponRegroup(6, 4_000L);
        assertFalse(session.couponRegrouping(6));
    }

    @Test
    void bossEdgeRegroupUsesAStageNineAbsoluteClock() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.transition(AgentLpqSession.Phase.STAGE_9, 2_000L);

        assertEquals(0L, session.observeBossEdgeRegroup(true, 10_000L));
        assertEquals(15_000L, session.observeBossEdgeRegroup(true, 25_000L));
        assertTrue(AgentLpqCoordinator.stageNineBossRegroupRecoveryDue(15_000L));
        assertEquals(0L, session.observeBossEdgeRegroup(false, 26_000L));
        assertEquals(0L, session.observeBossEdgeRegroup(true, 40_000L));

        session.transition(AgentLpqSession.Phase.BONUS, 41_000L);
        assertEquals(0L, session.observeBossEdgeRegroup(true, 60_000L));
    }

    @Test
    void bossEdgeFormationKeepsThePartyInAuthoredOpenFloorSpace() {
        assertTrue(AgentLpqCoordinator.stageNineBossAtEdge(new Point(-130, 184)));
        assertTrue(AgentLpqCoordinator.stageNineBossAtEdge(new Point(1_030, 184)));
        assertFalse(AgentLpqCoordinator.stageNineBossAtEdge(new Point(450, 184)));

        Point awayFromLeft = AgentLpqCoordinator.stageNineOpenSpaceAnchor(
                new Point(-200, 184), 101);
        Point awayFromRight = AgentLpqCoordinator.stageNineOpenSpaceAnchor(
                new Point(1_100, 184), 101);
        assertTrue(awayFromLeft.x > awayFromRight.x);
        assertEquals(184, awayFromLeft.y);
        assertEquals(184, awayFromRight.y);
        assertTrue(awayFromLeft.x >= 460 && awayFromLeft.x <= 580);
        assertTrue(awayFromRight.x >= 320 && awayFromRight.x <= 440);
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

    @Test
    void rewardClaimsAreFrozenAtomicAndResolvedPerRegisteredMember() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 5, 1_000L);
        session.addMember(101, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(104, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(900, AgentLpqMemberState.MemberType.HUMAN);

        assertFalse(session.beginRewardClaim(900));
        session.freezeRewardEligibility();
        assertTrue(session.beginRewardClaim(900));
        assertFalse(session.beginRewardClaim(900));
        session.cancelRewardClaim(900);
        assertTrue(session.beginRewardClaim(900));
        assertTrue(session.completeRewardClaim(900));
        assertFalse(session.beginRewardClaim(999));
        for (int id = 101; id <= 104; id++) session.forfeitReward(id);

        assertTrue(session.member(900).rewardClaimed());
        assertTrue(session.allRewardsResolved());
    }
}
