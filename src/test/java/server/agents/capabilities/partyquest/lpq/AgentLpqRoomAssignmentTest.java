package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqRoomAssignmentTest {
    @Test
    void reservationsAreExclusiveAndExpireWithoutProgress() {
        AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
        assertTrue(rooms.reserve(922_010_501, 101, 1_000L));
        assertFalse(rooms.reserve(922_010_501, 102, 1_100L));
        assertEquals(101, rooms.owner(922_010_501));

        rooms.markProgress(922_010_501, 2_000L);
        rooms.releaseExpired(2_500L, 1_000L);
        assertEquals(101, rooms.owner(922_010_501));
        var expired = rooms.releaseExpired(3_001L, 1_000L);
        assertNull(rooms.owner(922_010_501));
        assertEquals(java.util.List.of(
                new AgentLpqRoomAssignment.ExpiredReservation(922_010_501, 101)), expired);
    }

    @Test
    void completedRoomIsRememberedUntilStageReset() {
        AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
        rooms.reserve(922_010_506, 101, 0L);
        rooms.complete(922_010_506);
        assertTrue(rooms.completed(922_010_506));
        assertNull(rooms.owner(922_010_506));
        assertFalse(rooms.reserve(922_010_506, 101, 100L));
        assertFalse(rooms.reserve(922_010_506, 102, 100L));
        assertNull(rooms.owner(922_010_506));
        rooms.reset();
        assertFalse(rooms.completed(922_010_506));
        assertTrue(rooms.reserve(922_010_506, 102, 200L));
    }

    @Test
    void enteredRoomsRemainKnownAfterMembersExitButResetForTheNextStage() {
        AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
        java.util.List<Integer> authored = java.util.List.of(501, 502);

        rooms.reserve(501, 101, 1_000L);
        rooms.reserve(502, 102, 1_000L);
        assertFalse(rooms.enteredAll(authored));
        rooms.markEntered(501);
        rooms.complete(501);
        assertFalse(rooms.enteredAll(authored));
        rooms.markEntered(502);
        assertTrue(rooms.enteredAll(authored));

        rooms.reset();
        assertFalse(rooms.enteredAll(authored));
    }

    @Test
    void visibleDoorMarkerPersistsForAssignmentAndResetsWhenRoomChanges() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.TELEPORT_RUNNER, 922_010_501);
        assertFalse(member.roomMarkerDroppedFor(922_010_501));

        member.markRoomMarkerDropped(922_010_501);
        assertTrue(member.roomMarkerDroppedFor(922_010_501));
        assertFalse(member.roomMarkerDroppedFor(922_010_502));

        member.assign(AgentLpqMemberState.Role.GENERAL, 922_010_502);
        assertFalse(member.roomMarkerDroppedFor(922_010_501));
        assertFalse(member.roomMarkerDroppedFor(922_010_502));
    }

    @Test
    void roomPassCollectionUsesAnAssignmentSpecificInventoryBaseline() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.MAGIC_ATTACKER, 922_010_401);
        member.beginRoomPassCollection(922_010_401, 6);

        assertEquals(0, member.roomPassesCollectedFor(922_010_401, 6));
        assertEquals(1, member.roomPassesCollectedFor(922_010_401, 7));
        assertEquals(0, member.roomPassesCollectedFor(922_010_402, 7));

        member.assign(AgentLpqMemberState.Role.MAGIC_ATTACKER, 922_010_403);
        assertEquals(0, member.roomPassesCollectedFor(922_010_401, 7));
    }

    @Test
    void roomCombatStallClockResetsOnlyForTargetOrHpProgress() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.MAGIC_ATTACKER, 922_010_401);

        assertEquals(0L, member.observeRoomCombatTarget(922_010_401, 5001, 7_100, 1_000L));
        assertEquals(4_000L, member.observeRoomCombatTarget(922_010_401, 5001, 7_100, 5_000L));
        assertEquals(0L, member.observeRoomCombatTarget(922_010_401, 5001, 6_900, 5_100L));
        assertEquals(0L, member.observeRoomCombatTarget(922_010_401, 5002, 7_100, 8_000L));
    }

    @Test
    void roomTelemetryReportsChangesAndPeriodicHeartbeats() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertTrue(member.shouldReportRoomProgress(922_010_501, "4:0:0:0", 1_000L, 10_000L));
        assertFalse(member.shouldReportRoomProgress(922_010_501, "4:0:0:0", 5_000L, 10_000L));
        assertTrue(member.shouldReportRoomProgress(922_010_501, "3:0:1:0", 5_001L, 10_000L));
        assertTrue(member.shouldReportRoomProgress(922_010_501, "3:0:1:0", 15_001L, 10_000L));
    }

    @Test
    void assignmentHandoverClearsCompletedRoomExitContext() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.TELEPORT_RUNNER, 922_010_501);

        member.beginRoomExit(922_010_501, 1_000L);
        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertThrows(IllegalArgumentException.class,
                () -> member.markRoomExitProtectionPrepared(922_010_501));
        assertFalse(member.roomExitProtectionPreparedFor(922_010_501));
    }

    @Test
    void completedRoomExitProtectionIsScopedToTheRoomAndResetAfterLeaving() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.DARK_SIGHT_RUNNER, 922_010_506);
        member.beginRoomExit(922_010_506, 1_000L);

        assertFalse(member.roomExitProtectionPreparedFor(922_010_506));
        member.markRoomExitProtectionPrepared(922_010_506);
        assertTrue(member.roomExitProtectionPreparedFor(922_010_506));
        assertFalse(member.roomExitProtectionPreparedFor(922_010_505));

        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertFalse(member.roomExitProtectionPreparedFor(922_010_506));
    }

    @Test
    void completedRoomGetsItsOwnNaturalExitGrace() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.MAGIC_ATTACKER, 922_010_401);

        member.beginRoomExit(922_010_401, 10_000L);
        assertEquals(0L, member.roomExitElapsed(922_010_401, 10_000L));
        assertEquals(15_000L, member.roomExitElapsed(922_010_401, 25_000L));

        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertEquals(0L, member.roomExitElapsed(922_010_401, 40_000L));
    }

    @Test
    void passReportsUseEveryPassForRoomsAndFivePassMilestonesForLargeStages() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertTrue(member.shouldReportPassProgress(1, 922_010_100, 1, 25));
        assertFalse(member.shouldReportPassProgress(1, 922_010_100, 4, 25));
        assertTrue(member.shouldReportPassProgress(1, 922_010_100, 5, 25));
        assertTrue(member.shouldReportPassProgress(5, 922_010_503, 1, 4));
        assertTrue(member.shouldReportPassProgress(5, 922_010_503, 2, 4));
        assertFalse(member.shouldReportPassProgress(5, 922_010_503, 2, 4));
    }

    @Test
    void stageResetAtomicallyDiscardsPriorStageWorkAndProgressClocks() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.DARK_SIGHT_RUNNER, 922_010_506);
        member.assignPlatform(4);
        member.deferUntil(99_000L);
        member.commitReactorTarget(922_010_506, 55, 1_000L);
        member.beginRoomExit(922_010_506, 1_000L);
        member.markRoomExitProtectionPrepared(922_010_506);
        member.observeTraversalProgress(922_010_506, 922_010_500, 10_000L, 1_000L);
        member.observeNpcRallyProgress(5, 922_010_500, 10_000L, 1_000L);
        member.markCouponRegroupRecovered(5);
        assertTrue(member.shouldReportPassProgress(5, 922_010_506, 1, 4));

        member.resetForStage(AgentLpqMemberState.Role.GENERAL);

        assertEquals(AgentLpqMemberState.Role.GENERAL, member.role());
        assertEquals(0, member.assignedMapId());
        assertEquals(0, member.assignedPlatform());
        assertEquals(0L, member.nextActionAtMs());
        assertEquals(0, member.reactorTargetObjectId());
        assertFalse(member.roomExitProtectionPreparedFor(922_010_506));
        assertFalse(member.couponRegroupRecoveredFor(5));
        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 20_000L, 2_000L));
        assertEquals(0L, member.observeNpcRallyProgress(
                6, 922_010_600, 20_000L, 2_000L));
        assertTrue(member.shouldReportPassProgress(5, 922_010_506, 1, 4));
    }

    @Test
    void roomApproachHeartbeatRequiresRealMovementAndResetsOnHandover() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.DARK_SIGHT_RUNNER, 922_010_506);

        assertTrue(member.observeRoomApproachProgress(922_010_506, new Point(0, 0)));
        assertFalse(member.observeRoomApproachProgress(922_010_506, new Point(8, 8)));
        assertTrue(member.observeRoomApproachProgress(922_010_506, new Point(20, 0)));

        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertFalse(member.observeRoomApproachProgress(922_010_506, new Point(40, 0)));
    }

    @Test
    void traversalRecoveryMeasuresLackOfForwardProgressPerMember() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 100_000L, 1_000L));
        assertEquals(20_000L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 100_000L, 21_000L));
        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 90_000L, 22_000L));
        assertEquals(45_000L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 90_000L, 67_000L));

        member.clearTraversalProgress();
        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 1_000_000L, 70_000L));
        assertEquals(10_000L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 998_001L, 80_000L));
        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 968_256L, 81_000L));

        member.clearTraversalProgress();
        assertEquals(0L, member.observeTraversalProgress(
                922_010_500, 922_010_600, 90_000L, 68_000L));
    }

    @Test
    void authoredDetourMovementResetsRecoveryEvenWhenFartherFromTheFinalPortal() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        assertEquals(0L, member.observeTraversalProgress(
                922_010_401, 922_010_400, new Point(0, 0), 100_000L, 1_000L));
        assertEquals(10_000L, member.observeTraversalProgress(
                922_010_401, 922_010_400, new Point(8, 0), 101_000L, 11_000L));
        assertEquals(0L, member.observeTraversalProgress(
                922_010_401, 922_010_400, new Point(24, 0), 110_000L, 12_000L));
        assertEquals(14_999L, member.observeTraversalProgress(
                922_010_401, 922_010_400, new Point(24, 0), 110_000L, 26_999L));
    }

    @Test
    void npcRallyRecoveryDoesNotReusePortalTraversalHistory() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                101, AgentLpqMemberState.MemberType.AGENT);

        member.observeTraversalProgress(922_010_200, 922_010_201, 10_000L, 1_000L);
        assertEquals(0L, member.observeNpcRallyProgress(
                2, 922_010_200, 90_000L, 80_000L));
        assertEquals(44_999L, member.observeNpcRallyProgress(
                2, 922_010_200, 90_000L, 124_999L));
        assertEquals(0L, member.observeNpcRallyProgress(
                3, 922_010_300, 90_000L, 125_000L));
        member.clearNpcRallyProgress();
        assertEquals(0L, member.observeNpcRallyProgress(
                3, 922_010_300, 90_000L, 200_000L));
    }
}
