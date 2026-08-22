package server.agents.runtime.activity.session.adapter;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.kpq.AgentKpqMemberState;
import server.agents.capabilities.partyquest.kpq.AgentKpqSession;
import server.agents.capabilities.partyquest.kpq.AgentKpqSessionRegistry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionContractVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyQuestActivitySessionAdapterTest {
    @Test
    void projectsExistingKpqOwnershipWithoutStartingOrMutatingIt() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.AGENT);
        AgentKpqSessionRegistry.registerComplete(session);
        try {
            PartyQuestActivitySessionAdapter adapter =
                    new PartyQuestActivitySessionAdapter(101, null);
            var snapshot = adapter.snapshot(2_000L);

            assertEquals(AgentActivityKind.PARTY_QUEST, snapshot.kind());
            assertEquals(AgentActivityPhase.ACTIVE, snapshot.phase());
            assertEquals(session.sessionId(), snapshot.sessionId());
            assertTrue(AgentActivitySessionContractVerifier.snapshotIssues(snapshot).isEmpty());
            assertEquals(AgentActivityAdmissionResult.Status.REJECTED,
                    adapter.requestEntry(2_000L).status());
            assertEquals(server.agents.runtime.activity.session.AgentActivityExitResult.Status.REQUESTED,
                    adapter.requestGracefulExit("handoff", 2_001L, 3_000L).status());
            assertEquals(AgentActivityPhase.SUSPENDED, adapter.snapshot(2_002L).phase());
            assertEquals(server.agents.runtime.activity.session.AgentActivityRollbackPort.Result.Status.RESUMED,
                    adapter.resumeExact(session.sessionId(), 2_003L).status());
            assertEquals(AgentActivityPhase.ACTIVE, adapter.snapshot(2_004L).phase());
        } finally {
            AgentKpqSessionRegistry.remove(session);
        }
    }

    @Test
    void delegatesAdmissionToTheCallerOwnedPartyQuestRequest() {
        PartyQuestActivitySessionAdapter adapter = new PartyQuestActivitySessionAdapter(
                101, nowMs -> AgentActivityAdmissionResult.deferred("party forming", nowMs + 1_000L));

        AgentActivityAdmissionResult result = adapter.requestEntry(2_000L);

        assertEquals(AgentActivityAdmissionResult.Status.DEFERRED, result.status());
        assertEquals(3_000L, result.retryAtMs());
    }
}
