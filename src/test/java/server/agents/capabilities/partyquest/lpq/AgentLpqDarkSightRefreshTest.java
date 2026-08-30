package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqDarkSightRefreshTest {
    @Test
    void refreshesAtTenSecondsRemainingButNotBefore() {
        long startedAt = 1_000_000L;
        int durationMs = 200_000;

        assertFalse(AgentLpqCoordinator.darkSightRefreshDue(
                startedAt, durationMs, startedAt + 189_999L));
        assertTrue(AgentLpqCoordinator.darkSightRefreshDue(
                startedAt, durationMs, startedAt + 190_000L));
        assertTrue(AgentLpqCoordinator.darkSightRefreshDue(
                startedAt, durationMs, startedAt + durationMs));
    }

    @Test
    void missingOrInvalidBuffIsImmediatelyDue() {
        assertTrue(AgentLpqCoordinator.darkSightRefreshDue(null, 200_000, 1_000_000L));
        assertTrue(AgentLpqCoordinator.darkSightRefreshDue(1_000_000L, 0, 1_000_000L));
    }

    @Test
    void darkSightIsPreparedOnlyBeforeTheHazardRoomTraversal() {
        assertTrue(AgentLpqCoordinator.requiresDarkSightBeforeHazardTraversal(922_010_506, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeHazardTraversal(922_010_506, false));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeHazardTraversal(922_010_505, true));
    }

    @Test
    void darkSightIsOnlyRecastBeforeRoomExitWhenItsRefreshWindowIsDue() {
        assertTrue(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_506, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_506, false));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                4, 922_010_506, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_505, true));
    }

    @Test
    void stageFiveRecoveryNeverSweepsHazardMobs() {
        assertFalse(AgentLpqCoordinator.missingPassMobSweepAllowed(5));
        assertTrue(AgentLpqCoordinator.missingPassMobSweepAllowed(4));
    }

    @Test
    void darkSightRoomAlternatesProtectedTraversalAndOrdinaryBoxAttacks() {
        assertEquals(AgentLpqCoordinator.DarkSightRoomAction.CAST_FOR_TRAVERSAL,
                AgentLpqCoordinator.darkSightRoomAction(false, false, false));
        assertEquals(AgentLpqCoordinator.DarkSightRoomAction.NONE,
                AgentLpqCoordinator.darkSightRoomAction(true, false, false));
        assertEquals(AgentLpqCoordinator.DarkSightRoomAction.CANCEL_FOR_ATTACK,
                AgentLpqCoordinator.darkSightRoomAction(true, true, false));
        assertEquals(AgentLpqCoordinator.DarkSightRoomAction.NONE,
                AgentLpqCoordinator.darkSightRoomAction(false, true, false));
        assertEquals(AgentLpqCoordinator.DarkSightRoomAction.NONE,
                AgentLpqCoordinator.darkSightRoomAction(false, false, true));
    }

    @Test
    void darkSightRunnerRemainsProtectedOnTheStageFiveMainMap() {
        assertTrue(AgentLpqCoordinator.requiresStageFiveMainMapDarkSight(
                true, 922_010_500, true));
        assertFalse(AgentLpqCoordinator.requiresStageFiveMainMapDarkSight(
                true, 922_010_500, false));
        assertFalse(AgentLpqCoordinator.requiresStageFiveMainMapDarkSight(
                false, 922_010_500, true));
        assertFalse(AgentLpqCoordinator.requiresStageFiveMainMapDarkSight(
                true, 922_010_600, true));
    }

    @Test
    void darkSightRunnerCancelsProtectionOnlyAfterReachingTheStageFiveBalloon() {
        assertTrue(AgentLpqCoordinator.shouldCancelStageFiveDarkSightAtBalloon(
                true, 922_010_500, true, true));
        assertFalse(AgentLpqCoordinator.shouldCancelStageFiveDarkSightAtBalloon(
                true, 922_010_500, false, true));
        assertFalse(AgentLpqCoordinator.shouldCancelStageFiveDarkSightAtBalloon(
                true, 922_010_506, true, true));
        assertFalse(AgentLpqCoordinator.shouldCancelStageFiveDarkSightAtBalloon(
                false, 922_010_500, true, true));
        assertFalse(AgentLpqCoordinator.shouldCancelStageFiveDarkSightAtBalloon(
                true, 922_010_500, true, false));
    }

    @Test
    void darkSightIsCancelledToCollectAReachableRoomPass() {
        assertTrue(AgentLpqCoordinator.requiresDarkSightCancellationForRoomLoot(true, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightCancellationForRoomLoot(true, false));
        assertFalse(AgentLpqCoordinator.requiresDarkSightCancellationForRoomLoot(false, true));
    }
}
