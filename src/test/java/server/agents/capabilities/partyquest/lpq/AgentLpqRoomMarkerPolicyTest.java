package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqRoomMarkerPolicyTest {
    @Test
    void markerRequiresTheActualDoorPlatformInsteadOfTheFootholdBelow() {
        Point door = new Point(120, -600);

        assertTrue(AgentLpqCoordinator.atDoorMarkerPosition(
                new Point(142, -590), door));
        assertFalse(AgentLpqCoordinator.atDoorMarkerPosition(
                new Point(120, -540), door));
    }

    @Test
    void preservesLowPotionMarkersOnlyBesideActiveStageFourAndFiveDoors() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 91_001, 5, 1_000L);
        session.addMember(91_001, AgentLpqMemberState.MemberType.AGENT);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(91_001);
        AgentLpqSessionRegistry.registerComplete(session);
        try {
            session.transition(AgentLpqSession.Phase.STAGE_4, 1_100L);
            when(agent.getMapId()).thenReturn(922_010_400);
            assertTrue(AgentLpqSessionRegistry.preservesRoomDoorMarker(
                    agent, AgentLpqDefinition.RED_POTION));
            assertFalse(AgentLpqSessionRegistry.preservesRoomDoorMarker(
                    agent, AgentLpqDefinition.PASS));

            session.transition(AgentLpqSession.Phase.STAGE_5, 1_200L);
            when(agent.getMapId()).thenReturn(922_010_500);
            assertTrue(AgentLpqSessionRegistry.preservesRoomDoorMarker(
                    agent, AgentLpqDefinition.RED_POTION));

            session.transition(AgentLpqSession.Phase.STAGE_6, 1_300L);
            assertFalse(AgentLpqSessionRegistry.preservesRoomDoorMarker(
                    agent, AgentLpqDefinition.RED_POTION));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }
}
