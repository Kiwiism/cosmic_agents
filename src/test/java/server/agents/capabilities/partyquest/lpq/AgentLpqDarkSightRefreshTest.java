package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

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
                5, 922_010_506, false, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_506, false, false));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_506, true, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                4, 922_010_506, false, true));
        assertFalse(AgentLpqCoordinator.requiresDarkSightBeforeRoomExit(
                5, 922_010_505, false, true));
    }

    @Test
    void stageFiveRecoveryNeverSweepsHazardMobs() {
        assertFalse(AgentLpqCoordinator.missingPassMobSweepAllowed(5));
        assertTrue(AgentLpqCoordinator.missingPassMobSweepAllowed(4));
    }
}
