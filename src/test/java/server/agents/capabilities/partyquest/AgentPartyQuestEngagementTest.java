package server.agents.capabilities.partyquest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyQuestEngagementTest {
    @Test
    void oneEngagementSpansLobbyFreshEventAndPostRunHold() {
        AgentPartyQuestEngagement engagement = engagement(100, 1_000L);
        engagement.addMember(101, AgentPartyQuestEngagement.MemberType.AGENT, 1_001L);
        engagement.addMember(100, AgentPartyQuestEngagement.MemberType.HUMAN, 1_002L);

        engagement.beginLobby("lobby-1", 1_100L);
        engagement.lobbyReady(1_200L);
        engagement.reserveEntry(1_300L);
        engagement.activateSession("session-1", 1_400L);
        engagement.finishRun(true, "completed", 2_000L);

        assertEquals(AgentPartyQuestEngagement.State.POST_RUN_HOLD, engagement.state());
        assertTrue(engagement.ownsAgent(101));
        assertFalse(engagement.ownsAgent(100));

        engagement.beginLobby("lobby-2", 2_100L);
        engagement.lobbyReady(2_200L);
        engagement.reserveEntry(2_300L);
        engagement.activateSession("session-2", 2_400L);
        assertNotEquals("session-1", engagement.activeSessionId());
    }

    @Test
    void registryRejectsOverlappingMemberOwnership() {
        AgentPartyQuestEngagement first = engagement(100, 1_000L);
        first.addMember(101, AgentPartyQuestEngagement.MemberType.AGENT, 1_001L);
        AgentPartyQuestEngagement second = engagement(200, 1_000L);
        second.addMember(101, AgentPartyQuestEngagement.MemberType.AGENT, 1_001L);
        AgentPartyQuestEngagementRegistry.register(first);
        try {
            assertThrows(IllegalStateException.class,
                    () -> AgentPartyQuestEngagementRegistry.register(second));
        } finally {
            AgentPartyQuestEngagementRegistry.remove(first);
            AgentPartyQuestEngagementRegistry.remove(second);
        }
    }

    @Test
    void dynamicMemberPublicationRollsBackBeforeMutationOnConflict() {
        AgentPartyQuestEngagement first = engagement(100, 1_000L);
        AgentPartyQuestEngagement second = engagement(200, 1_000L);
        AgentPartyQuestEngagementRegistry.register(first);
        AgentPartyQuestEngagementRegistry.register(second);
        try {
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    first, 101, AgentPartyQuestEngagement.MemberType.AGENT, 1_001L);
            assertThrows(IllegalStateException.class,
                    () -> AgentPartyQuestEngagementRegistry.addAndIndexMember(
                            second, 101, AgentPartyQuestEngagement.MemberType.AGENT, 1_002L));
            assertFalse(second.memberIds().contains(101));
        } finally {
            AgentPartyQuestEngagementRegistry.remove(first);
            AgentPartyQuestEngagementRegistry.remove(second);
        }
    }

    @Test
    void recoveryDiagnosticsAndWarningsAreBounded() {
        AgentPartyQuestEngagement engagement = engagement(100, 1_000L);
        engagement.beginRecovery("recovering", 1_100L);
        for (int index = 0; index < 100; index++) {
            engagement.addDiagnostic("failure-" + index, 1_200L + index);
        }
        assertEquals(64, engagement.diagnostics().size());
        assertTrue(engagement.claimRecoveryWarning(2_000L, 1_000L));
        assertFalse(engagement.claimRecoveryWarning(2_999L, 1_000L));
        assertTrue(engagement.claimRecoveryWarning(3_000L, 1_000L));
    }

    @Test
    void repeatedRecoveryDoesNotResetTheRecoveryDeadline() {
        AgentPartyQuestEngagement engagement = engagement(100, 1_000L);
        engagement.beginRecovery("first", 1_100L);
        engagement.beginRecovery("second", 5_000L);
        assertEquals(1_100L, engagement.stateEnteredAtMs());
        assertEquals(AgentPartyQuestEngagement.State.RECOVERING, engagement.state());
    }

    private static AgentPartyQuestEngagement engagement(int operatorId, long nowMs) {
        return new AgentPartyQuestEngagement(
                "kpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                7L, operatorId, 4, nowMs);
    }
}
