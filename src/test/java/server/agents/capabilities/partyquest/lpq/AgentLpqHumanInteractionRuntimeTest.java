package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqHumanInteractionRuntimeTest {
    @Test
    void humanLeaderWrongCheckAdvancesFormationOnlyAfterAuthoredCallback() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 6, 1_000L);
        session.addMember(900, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(101, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(900, 101);
        session.transition(AgentLpqSession.Phase.STAGE_8, 1_100L);
        AgentLpqSessionRegistry.registerComplete(session);
        try {
            Character leader = mock(Character.class);
            when(leader.getId()).thenReturn(900);
            AgentLpqHumanInteractionRuntime.stageEightChecked(leader, false, 1_200L);
            assertEquals(1, session.stage8Attempt());
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }
}
