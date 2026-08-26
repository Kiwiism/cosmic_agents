package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLpqStageEightPlatformTest {
    @Test
    void navigationApproachesPlatformFromAboveInsteadOfBelowItsFoothold() {
        Rectangle authoredArea = new Rectangle(-247, -232, 61, 25);

        assertEquals(new Point(-216, -232),
                AgentLpqCoordinator.stageEightPlatformTarget(authoredArea));
    }

    @Test
    void changingCombinationClearsTheMoversPreviousTraversalClock() {
        AgentLpqMemberState mover = new AgentLpqMemberState(
                10, AgentLpqMemberState.MemberType.AGENT);
        mover.assignPlatform(5);
        assertEquals(0L, mover.observeTraversalProgress(
                922_010_800, 922_010_800, 10_000L, 1_000L));
        assertEquals(30_000L, mover.observeTraversalProgress(
                922_010_800, 922_010_800, 10_000L, 31_000L));

        mover.assignPlatform(6);

        assertEquals(0L, mover.observeTraversalProgress(
                922_010_800, 922_010_800, 10_000L, 32_000L));
    }

    @Test
    void assignmentChatMapsEachCharacterNameOrIdToItsBox() {
        assertEquals("Stage 8: 10->1, 20->3, 30->6, 40->7, 50->4",
                AgentLpqCoordinator.stageEightAssignmentChat(java.util.Map.of(
                        50, 4, 30, 6, 10, 1, 40, 7, 20, 3)));
    }
}
