package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKpqCoordinatorTest {
    @Test
    void completeCommandRecoversPassesThatEveryMemberAlreadyDelivered() {
        Character leader = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(leader.getId()).thenReturn(101);
        when(leader.getName()).thenReturn("KPQer01");
        when(leader.getMap()).thenReturn(map);
        when(leader.getMapId()).thenReturn(AgentKpqDefinition.STAGE_1_MAP);
        when(leader.getEventInstance()).thenReturn(event);
        when(map.getDroppedItems()).thenReturn(List.of());
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.AGENT);
        session.member(102).markPassDelivered();
        session.member(103).markPassDelivered();

        assertTrue(AgentKpqCoordinator.recoverMissingStageOnePassesForTestCommand(
                session, leader, 2_000L));

        verify(event).setProperty("1stageclear", "true");
        verify(event).linkToNextStage(1, "kpq", AgentKpqDefinition.STAGE_1_MAP);
    }

    @Test
    void reportsOnlyTheMemberWhoseAssignmentChanged() {
        AgentKpqMemberState first = member(1, 1, 1);
        AgentKpqMemberState second = member(2, 2, 2);
        AgentKpqMemberState third = member(3, 3, 3);
        List<AgentKpqMemberState> participants = List.of(first, second, third);

        List<AgentKpqMemberState> movers = AgentKpqCoordinator.assignFormation(
                participants, List.of(1, 2, 4), 7L, 2, 1_000L);

        assertEquals(List.of(third), movers);
        assertEquals(1, first.assignedPosition());
        assertEquals(2, second.assignedPosition());
        assertEquals(4, third.assignedPosition());
    }

    @Test
    void reportsNoMovementWhenFormationIsAlreadyCorrect() {
        List<AgentKpqMemberState> participants = List.of(
                member(1, 1, 1), member(2, 2, 2), member(3, 3, 4));

        assertTrue(AgentKpqCoordinator.assignFormation(
                participants, List.of(1, 2, 4), 7L, 3, 1_000L).isEmpty());
    }

    @Test
    void reportsEveryCrossedCouponMilestoneWithCompletion() {
        assertEquals(List.of(20), AgentKpqCoordinator.crossedCouponMilestones(0, 2, 10));
        assertEquals(List.of(50), AgentKpqCoordinator.crossedCouponMilestones(20, 5, 10));
        assertEquals(List.of(90), AgentKpqCoordinator.crossedCouponMilestones(50, 9, 10));
        assertEquals(List.of(100), AgentKpqCoordinator.crossedCouponMilestones(90, 10, 10));
        assertEquals(List.of(90, 100),
                AgentKpqCoordinator.crossedCouponMilestones(50, 8, 8));
    }

    @Test
    void confirmsPassDeliveryOnlyWhenTheMembersInventoryActuallyDecreases() {
        assertTrue(AgentKpqCoordinator.passDropConfirmed(1, 0));
        assertTrue(AgentKpqCoordinator.passDropConfirmed(2, 1));
        assertFalse(AgentKpqCoordinator.passDropConfirmed(1, 1));
        assertFalse(AgentKpqCoordinator.passDropConfirmed(0, 0));
    }

    @Test
    void bypassesOnlyAfterEveryDeliveryIsDoneAndNoPassRemainsOnTheFloor() {
        assertTrue(AgentKpqCoordinator.shouldBypassMissingPasses(
                true, 2, 3, 0, 1_000L, 6_000L, 5_000L));
        assertFalse(AgentKpqCoordinator.shouldBypassMissingPasses(
                false, 2, 3, 0, 1_000L, 6_000L, 5_000L));
        assertFalse(AgentKpqCoordinator.shouldBypassMissingPasses(
                true, 2, 3, 1, 1_000L, 6_000L, 5_000L));
        assertFalse(AgentKpqCoordinator.shouldBypassMissingPasses(
                true, 2, 3, 0, 1_000L, 5_999L, 5_000L));
        assertFalse(AgentKpqCoordinator.shouldBypassMissingPasses(
                true, 3, 3, 0, 1_000L, 6_000L, 5_000L));
    }

    @Test
    void puzzleCheckDelayIsDeterministicAndWithinConfiguredVariation() {
        long first = AgentKpqCoordinator.puzzleCheckDelayMs(77L, 3, 4);
        long repeated = AgentKpqCoordinator.puzzleCheckDelayMs(77L, 3, 4);

        assertEquals(first, repeated);
        assertTrue(first >= 1_350L && first <= 2_650L);
    }

    @Test
    void humansOwnExactlyTheFirstSevenSecondsOfSquishyShoesLoot() {
        assertTrue(AgentKpqCoordinator.squishyShoesHumanWindowActive(
                true, 1_000L, 7_999L, 7_000L));
        assertFalse(AgentKpqCoordinator.squishyShoesHumanWindowActive(
                true, 1_000L, 8_000L, 7_000L));
        assertFalse(AgentKpqCoordinator.squishyShoesHumanWindowActive(
                false, 1_000L, 1_001L, 7_000L));
    }

    @Test
    void humanLootPriorityOnlyUsesCurrentSessionPartyMembers() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 4, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.AGENT);

        assertFalse(AgentKpqCoordinator.hasHumanPartyMember(session));

        session.addMember(104, AgentKpqMemberState.MemberType.HUMAN);
        assertTrue(AgentKpqCoordinator.hasHumanPartyMember(session));

        session.removeMember(104);
        assertFalse(AgentKpqCoordinator.hasHumanPartyMember(session));
    }

    @Test
    void stageEntryMovementDelayIsDeterministicAndStaggeredByPartyNumber() {
        AgentKpqMemberState first = member(101, 1, 0);
        AgentKpqMemberState fourth = member(104, 4, 0);

        long firstDelay = AgentKpqCoordinator.stageMovementDelayMs(77L, first, 2);
        long repeated = AgentKpqCoordinator.stageMovementDelayMs(77L, first, 2);
        long fourthDelay = AgentKpqCoordinator.stageMovementDelayMs(77L, fourth, 2);

        assertEquals(firstDelay, repeated);
        assertTrue(firstDelay >= 310L && firstDelay < 490L);
        assertTrue(fourthDelay >= 820L && fourthDelay < 1_000L);
    }

    @Test
    void waitsForEveryKingSlimeReviveBeforeReturningToCloto() {
        assertTrue(AgentKpqCoordinator.STAGE_5_NORMAL_MOBS.contains(210_100));
        assertFalse(AgentKpqCoordinator.stageFiveReadyToReturn(10, 1, 0, false));
        assertFalse(AgentKpqCoordinator.stageFiveReadyToReturn(10, 0, 0, true));
        assertTrue(AgentKpqCoordinator.stageFiveReadyToReturn(10, 0, 0, false));
    }

    @Test
    void boundedPuzzleFidgetDoesNotInvalidateTheAssignedPosition() {
        for (int stage = 2; stage <= 4; stage++) {
            AgentKpqDefinition.CombinationStage definition =
                    AgentKpqDefinition.combinationStage(stage);
            AgentKpqMemberState member = member(101, 1, 1);
            Point start = definition.center(1);
            Point fidget = AgentKpqPuzzleFidgetBehavior.target(
                    definition, member, start, 77L, 1);

            assertTrue(definition.contains(1, fidget));
            assertTrue(AgentKpqCoordinator.puzzlePositionReady(
                    definition, 1, fidget, true));
        }
    }

    @Test
    void startingFidgetDoesNotChangePuzzleReadinessOrCheckTiming() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 77L, 1, 3, 1_000L);
        AgentKpqMemberState member = member(101, 1, 1);
        member.setStableSinceMs(2_000L);
        member.setActionNotBeforeMs(1_500L);
        session.setPuzzleCheckAtMs(4_000L);
        AgentKpqDefinition.CombinationStage definition =
                AgentKpqDefinition.combinationStage(2);

        AgentKpqPuzzleFidgetBehavior.begin(member, definition, definition.center(1),
                session.seed(), 1, 2_500L, 5_000L);

        assertEquals(2_000L, member.stableSinceMs());
        assertEquals(1_500L, member.actionNotBeforeMs());
        assertEquals(4_000L, session.puzzleCheckAtMs());
    }

    @Test
    void onlyLeavingTheSlotOrLosingGroundContactInvalidatesReadiness() {
        AgentKpqDefinition.CombinationStage stageThree =
                AgentKpqDefinition.combinationStage(3);

        assertTrue(AgentKpqCoordinator.puzzlePositionReady(
                stageThree, 1, stageThree.center(1), true));
        assertFalse(AgentKpqCoordinator.puzzlePositionReady(
                stageThree, 1, new Point(0, 0), true));
        assertFalse(AgentKpqCoordinator.puzzlePositionReady(
                stageThree, 1, stageThree.center(1), false));
    }

    @Test
    void kingSlimeCombatAssessmentFlagsInactiveAndVeryLowContributors() {
        assertEquals("no-attacks", AgentKpqCoordinator.bossCombatConcern(
                new AgentKpqMemberState.BossCombatDelta(0, 0, 0, 0), 1_000));
        assertEquals("all-misses", AgentKpqCoordinator.bossCombatConcern(
                new AgentKpqMemberState.BossCombatDelta(4, 0, 4, 0), 1_000));
        assertEquals("accuracy-limited", AgentKpqCoordinator.bossCombatConcern(
                new AgentKpqMemberState.BossCombatDelta(10, 3, 7, 250), 1_000));
        assertEquals("below-10%-of-party-maximum", AgentKpqCoordinator.bossCombatConcern(
                new AgentKpqMemberState.BossCombatDelta(4, 4, 0, 99), 1_000));
        assertEquals("ok", AgentKpqCoordinator.bossCombatConcern(
                new AgentKpqMemberState.BossCombatDelta(4, 4, 0, 100), 1_000));
    }

    private static AgentKpqMemberState member(int characterId, int partyNumber, int position) {
        AgentKpqMemberState member = new AgentKpqMemberState(
                characterId, AgentKpqMemberState.MemberType.AGENT, partyNumber);
        member.setAssignedPosition(position);
        return member;
    }
}
