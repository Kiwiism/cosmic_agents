package server.agents.capabilities.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationRecoveryPolicyTest {
    @Test
    void offDoesNotRecordOrChangeRoutes() {
        assertFalse(AgentNavigationRecoveryPolicy.recordsRouteProgress(0));
        assertFalse(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(0, false));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformMovementRecovery(0));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformSoftTeleport(0));
    }

    @Test
    void observeOnlyRecordsWithoutChangingRoutes() {
        assertTrue(AgentNavigationRecoveryPolicy.recordsRouteProgress(1));
        assertFalse(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(1, false));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformMovementRecovery(1));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformSoftTeleport(1));
    }

    @Test
    void conservativeCannotOverrideAuthoredRouteOverlay() {
        assertTrue(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(2, false));
        assertFalse(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(2, true));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformMovementRecovery(2));
        assertFalse(AgentNavigationRecoveryPolicy.mayPerformSoftTeleport(2));
    }

    @Test
    void legacyAggressiveExplicitlyRestoresMovementRecovery() {
        assertTrue(AgentNavigationRecoveryPolicy.recordsRouteProgress(3));
        assertTrue(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(3, false));
        assertFalse(AgentNavigationRecoveryPolicy.mayRejectRouteEdge(3, true));
        assertTrue(AgentNavigationRecoveryPolicy.mayPerformMovementRecovery(3));
        assertTrue(AgentNavigationRecoveryPolicy.mayPerformSoftTeleport(3));
    }
}
