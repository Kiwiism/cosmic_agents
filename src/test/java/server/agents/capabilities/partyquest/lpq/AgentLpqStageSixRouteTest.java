package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqStageSixRouteTest {
    @Test
    void usesAuthoredPortalRouteWithoutProbingWrongPortals() {
        assertEquals(List.of(4, 7, 8, 12, 15, 17, 20, 23, 27, 29, 34, 36, 38, 41, 46),
                java.util.stream.IntStream.range(0, 15)
                        .map(AgentLpqCoordinator::stageSixPortalId)
                        .boxed().toList());
    }

    @Test
    void announcesTheCanonicalPlayerFacingBoxSequence() {
        assertEquals(List.of("Stage 6 sequence: 133 221 333 123 111"),
                AgentLpqCoordinator.stageSixSequenceChats());
    }

    @Test
    void agentLeaderAnnouncesOtherwiseAnAgentSubstituteDoes() {
        AgentLpqMemberState agentLeader = new AgentLpqMemberState(30, AgentLpqMemberState.MemberType.AGENT);
        AgentLpqMemberState humanLeader = new AgentLpqMemberState(40, AgentLpqMemberState.MemberType.HUMAN);
        AgentLpqMemberState firstAgent = new AgentLpqMemberState(10, AgentLpqMemberState.MemberType.AGENT);
        AgentLpqMemberState secondAgent = new AgentLpqMemberState(20, AgentLpqMemberState.MemberType.AGENT);

        assertEquals(30, AgentLpqCoordinator.stageSixAnnouncementSpeakerId(
                List.of(firstAgent, agentLeader, secondAgent), 30));
        assertEquals(10, AgentLpqCoordinator.stageSixAnnouncementSpeakerId(
                List.of(secondAgent, humanLeader, firstAgent), 40));
    }

    @Test
    void sequenceAnnouncementCompletesAfterTheFullRouteMessage() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.PRODUCTION, 1L, 1, 6, 100L);

        assertTrue(session.stage6SequenceChatReady(200L));
        session.markStage6SequenceAnnounced(200L);

        assertTrue(session.stage6SequenceAnnounced());
        assertEquals(200L, session.lastProgressAtMs());
    }
}
