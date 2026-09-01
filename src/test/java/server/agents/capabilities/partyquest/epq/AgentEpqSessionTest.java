package server.agents.capabilities.partyquest.epq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEpqSessionTest {
    private AgentEpqSession registered;

    @AfterEach
    void cleanup() {
        AgentEpqSessionRegistry.remove(registered);
    }

    @Test
    void registryAcceptsFourToSixMembersAndKeepsIndexesPrivate() {
        registered = session(4);
        AgentEpqSessionRegistry.registerComplete(registered);
        assertSame(registered, AgentEpqSessionRegistry.forMember(4));
        assertSame(registered, AgentEpqSessionRegistry.forOperator(1));
        assertEquals(1, AgentEpqSessionRegistry.sessions().size());
        AgentEpqSessionRegistry.remove(registered);
        assertFalse(AgentEpqSessionRegistry.active(4));
    }

    @Test
    void rejectsAnIncompleteParty() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentEpqSessionRegistry.registerComplete(session(3)));
    }

    @Test
    void transitionsForwardAndLeasesCoordination() {
        AgentEpqSession session = session(4);
        session.transition(AgentEpqSession.Phase.STAGE_ONE, 20L);
        session.transition(AgentEpqSession.Phase.ENTERING, 30L);
        assertEquals(AgentEpqSession.Phase.STAGE_ONE, session.phase());
        assertTrue(session.claimExecutionTick(1, 40L, 100L));
        assertFalse(session.claimExecutionTick(2, 50L, 100L));
        assertTrue(session.claimExecutionTick(2, 141L, 100L));
    }

    @Test
    void progressSignaturesRefreshTheStageWatchdogOnlyWhenEvidenceChanges() {
        AgentEpqSession session = session(5);
        session.observeProgressSignature(100L, 20L);
        session.observeProgressSignature(100L, 50L);
        assertEquals(20L, session.lastProgressAtMs());

        AgentEpqWatchdogRuntime.tick(session, 240_019L);
        assertFalse(session.terminal());
        AgentEpqWatchdogRuntime.tick(session, 240_020L);
        assertEquals(AgentEpqSession.Phase.FAILED, session.phase());
        assertTrue(session.failure().contains("stalled"));
    }

    @Test
    void stageFiveHasOneStoneCollectorAndStillAcceptsHumanLeadership() {
        AgentEpqSession agentsOnly = session(5);
        assertTrue(AgentEpqCoordinator.mayCollectStageFiveStone(agentsOnly, 1));
        assertFalse(AgentEpqCoordinator.mayCollectStageFiveStone(agentsOnly, 2));

        AgentEpqSession mixed = new AgentEpqSession(
                AgentEpqSession.Mode.HUMAN_ASSISTED, 7L, 10, 10L);
        mixed.addMember(10, AgentEpqMemberState.MemberType.HUMAN);
        mixed.addMember(11, AgentEpqMemberState.MemberType.AGENT);
        mixed.addMember(12, AgentEpqMemberState.MemberType.AGENT);
        mixed.addMember(13, AgentEpqMemberState.MemberType.AGENT);
        mixed.addMember(14, AgentEpqMemberState.MemberType.AGENT);
        mixed.setLeadership(10, 11);

        assertTrue(AgentEpqCoordinator.mayCollectStageFiveStone(mixed, 11));
        assertFalse(AgentEpqCoordinator.mayCollectStageFiveStone(mixed, 12));
        assertFalse(AgentEpqCoordinator.mayCollectStageFiveStone(mixed, 10));
    }

    private static AgentEpqSession session(int memberCount) {
        AgentEpqSession session = new AgentEpqSession(AgentEpqSession.Mode.TEST_OBSERVATION, 7L, 1, 10L);
        for (int id = 1; id <= memberCount; id++) {
            session.addMember(id, AgentEpqMemberState.MemberType.AGENT);
        }
        if (memberCount > 0) session.setLeadership(1, 1);
        return session;
    }
}
