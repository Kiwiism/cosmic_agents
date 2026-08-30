package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.maps.Reactor;
import server.maps.MapItem;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqReactorTargetTest {
    @Test
    void retainsOneReactorUntilItBreaksThenWaitsForSpawnCleanup() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_005, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.GENERAL, 922_010_502);
        Reactor first = reactor(101, new Point(10, 0));
        Reactor second = reactor(102, new Point(100, 0));

        assertSame(first, AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(0, 0), List.of(first, second), true));
        assertEquals(101, member.reactorTargetObjectId());

        assertSame(first, AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(100, 0), List.of(first, second), true));
        assertEquals(101, member.reactorTargetObjectId());
        member.markReactorTargetHit();
        assertTrue(member.reactorTargetHitOnce());

        assertNull(AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(100, 0), List.of(second), true));
        assertTrue(member.reactorSpawnCleanupPending());
        assertFalse(member.reactorTargetHitOnce());
        assertNull(AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(100, 0), List.of(second), true));

        member.finishReactorSpawnCleanup();
        assertSame(second, AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(100, 0), List.of(second), true));
        assertEquals(102, member.reactorTargetObjectId());
    }

    @Test
    void assignmentAndStageTransitionClearCommittedReactorWork() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_006, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.GENERAL, 922_010_502);
        member.commitReactorTarget(922_010_502, 201);
        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertEquals(0, member.reactorTargetObjectId());
        assertFalse(member.reactorTargetHitOnce());
        assertFalse(member.reactorSpawnCleanupPending());

        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 70_006, 5, 1_000L);
        session.addMember(71_006, AgentLpqMemberState.MemberType.AGENT);
        session.member(71_006).commitReactorTarget(922_010_502, 202);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);
        assertEquals(0, session.member(71_006).reactorTargetObjectId());
    }

    @Test
    void stageFiveUsesAuthoredRoomOrderInsteadOfStraightLineDistance() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_008, AgentLpqMemberState.MemberType.AGENT);
        Reactor authoredFirst = reactor(401, new Point(226, -921));
        Reactor geometricallyNearest = reactor(402, new Point(215, -3_009));

        assertSame(authoredFirst, AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_503, new Point(215, -3_000),
                List.of(geometricallyNearest, authoredFirst), false));
    }

    @Test
    void stageFiveApproachRemainsProvisionalUntilTheFirstHit() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_009, AgentLpqMemberState.MemberType.AGENT);

        member.beginReactorApproach(922_010_503, 421);
        assertEquals(421, member.reactorApproachObjectId());
        assertEquals(0, member.reactorTargetObjectId());

        member.commitReactorTarget(922_010_503, 421, 10_000L);
        assertEquals(0, member.reactorApproachObjectId());
        assertEquals(421, member.reactorTargetObjectId());
    }

    @Test
    void stageFiveOrderCatalogMatchesEveryAuthoredRoomReactor() {
        for (int roomMapId : AgentLpqDefinition.roomMaps(5)) {
            Data mapData = DataProviderFactory.getDataProvider(WZFiles.MAP)
                    .getData("Map/Map9/" + roomMapId + ".img");
            Data reactors = mapData.getChildByPath("reactor");
            Set<Point> authored = reactors.getChildren().stream()
                    .map(reactor -> new Point(
                            DataTool.getInt("x", reactor, 0),
                            DataTool.getInt("y", reactor, 0)))
                    .collect(Collectors.toSet());

            assertEquals(4, AgentLpqStageFiveReactorOrder.positions(roomMapId).size());
            assertEquals(authored, Set.copyOf(
                    AgentLpqStageFiveReactorOrder.positions(roomMapId)));
        }
    }

    @Test
    void darkSightFinalBoxUsesTheAuthoredTwoRopeAscent() {
        int room = AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM;
        Point finalBox = new Point(-70, -3_535);

        assertTrue(AgentLpqStageFiveReactorOrder.isDarkSightFinalBox(room, finalBox));
        assertEquals(new Point(-8, -1_518),
                AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                        room, new Point(-24, -1_427), finalBox));
        assertEquals(new Point(-8, -2_488),
                AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                        room, new Point(-8, -1_518), finalBox));
        assertEquals(new Point(13, -2_562),
                AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                        room, new Point(-8, -2_488), finalBox));
        assertEquals(new Point(13, -3_533),
                AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                        room, new Point(13, -2_562), finalBox));
        assertNull(AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                room, new Point(13, -3_533), finalBox));
        assertNull(AgentLpqStageFiveReactorOrder.authoredApproachWaypoint(
                room, new Point(-24, -1_427), new Point(-81, -1_459)));
    }

    @Test
    void stageFiveReplacesOnlyOneMissingPassAfterTheFinalDropGracePeriod() {
        assertTrue(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                5, 0, false, 3, 4, 3_000L));
        assertFalse(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                5, 0, false, 3, 4, 2_999L));
        assertFalse(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                5, 0, true, 3, 4, 10_000L));
        assertFalse(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                5, 1, false, 3, 4, 10_000L));
        assertFalse(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                5, 0, false, 2, 4, 10_000L));
        assertFalse(AgentLpqCoordinator.stageFiveMissingRoomPassRecoveryDue(
                4, 0, false, 3, 4, 10_000L));
    }

    @Test
    void stageFiveTerminalWatchdogRecoversOnlyTheFinalLoosePassAfterPickupGrace() {
        assertTrue(AgentLpqCoordinator.stageFiveFinalLoosePassRecoveryDue(
                3, 4, 2_000L));
        assertFalse(AgentLpqCoordinator.stageFiveFinalLoosePassRecoveryDue(
                3, 4, 1_999L));
        assertFalse(AgentLpqCoordinator.stageFiveFinalLoosePassRecoveryDue(
                2, 4, 10_000L));
        assertFalse(AgentLpqCoordinator.stageFiveFinalLoosePassRecoveryDue(
                4, 4, 10_000L));
    }

    @Test
    void reactorRecoveryClockRestartsForEveryBoxAndClearsOnExit() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_007, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.GENERAL, 922_010_503);
        member.commitReactorTarget(922_010_503, 301, 10_000L);
        member.markReactorTargetBroken(false);
        member.commitReactorTarget(922_010_503, 302, 80_000L);

        assertEquals(80_000L, member.reactorTargetCommittedAtMs());

        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertEquals(0L, member.reactorTargetCommittedAtMs());
    }

    @Test
    void ordinaryBoxesRequireGroundedSamePlatformInteraction() {
        Point box = new Point(100, 200);

        assertTrue(AgentLpqCoordinator.reactorInteractionReady(
                new Point(45, 200), true, box));
        assertFalse(AgentLpqCoordinator.reactorInteractionReady(
                new Point(45, 200), false, box));
        assertFalse(AgentLpqCoordinator.reactorInteractionReady(
                new Point(100, 145), true, box));
        assertFalse(AgentLpqCoordinator.reactorInteractionReady(
                new Point(20, 200), true, box));
    }

    @Test
    void stageTwoPartyClaimsKeepAgentsOnDifferentBoxes() {
        AgentLpqMemberState firstMember = new AgentLpqMemberState(
                72_001, AgentLpqMemberState.MemberType.AGENT);
        AgentLpqMemberState secondMember = new AgentLpqMemberState(
                72_002, AgentLpqMemberState.MemberType.AGENT);
        Reactor first = reactor(301, new Point(10, 0));
        Reactor second = reactor(302, new Point(30, 0));

        assertSame(first, AgentLpqCoordinator.selectCommittedReactor(
                firstMember, 922_010_200, new Point(0, 0), List.of(first, second),
                false, Set.of()));
        assertSame(second, AgentLpqCoordinator.selectCommittedReactor(
                secondMember, 922_010_200, new Point(0, 0), List.of(first, second),
                false, Set.of(first.getObjectId())));

        assertSame(first, AgentLpqCoordinator.selectCommittedReactor(
                firstMember, 922_010_200, new Point(30, 0), List.of(first, second),
                false, Set.of(first.getObjectId(), second.getObjectId())));
    }

    @Test
    void stageThreePartyClaimsKeepAgentsOnDifferentBoxes() {
        AgentLpqMemberState firstMember = new AgentLpqMemberState(
                73_001, AgentLpqMemberState.MemberType.AGENT);
        AgentLpqMemberState secondMember = new AgentLpqMemberState(
                73_002, AgentLpqMemberState.MemberType.AGENT);
        Reactor first = reactor(401, new Point(10, 0));
        Reactor second = reactor(402, new Point(30, 0));

        assertSame(first, AgentLpqCoordinator.selectCommittedReactor(
                firstMember, 922_010_300, new Point(0, 0), List.of(first, second),
                false, Set.of()));
        assertSame(second, AgentLpqCoordinator.selectCommittedReactor(
                secondMember, 922_010_300, new Point(0, 0), List.of(first, second),
                false, Set.of(first.getObjectId())));
    }

    @Test
    void countsOnlyUnpickedLpqPassDrops() {
        MapItem pass = mock(MapItem.class);
        when(pass.getItemId()).thenReturn(AgentLpqDefinition.PASS);
        MapItem pickedPass = mock(MapItem.class);
        when(pickedPass.getItemId()).thenReturn(AgentLpqDefinition.PASS);
        when(pickedPass.isPickedUp()).thenReturn(true);
        MapItem other = mock(MapItem.class);
        when(other.getItemId()).thenReturn(2_000_003);

        assertEquals(1, AgentLpqCoordinator.unpickedPassDropCount(
                List.of(pass, pickedPass, other)));
    }

    @Test
    void stageTwoPassBelongsToTheNearestAgentRatherThanRosterOrder() {
        Point drop = new Point(100, -500);

        assertEquals(72_006, AgentLpqCoordinator.nearestAgentIdToDrop(
                Map.of(72_001, new Point(-400, -500),
                        72_006, new Point(105, -500),
                        72_004, new Point(300, -500)), drop));
    }

    @Test
    void includesDynamicallySpawnedStageSevenAndBossMonsters() {
        assertEquals(java.util.Set.of(9_300_170, AgentLpqDefinition.ROMBARD),
                AgentLpqCoordinator.stageCombatTargets(7, java.util.Set.of(9_300_170)));
        assertEquals(java.util.Set.of(
                        AgentLpqDefinition.BOSS_TRIGGER_RATZ, AgentLpqDefinition.ALISHAR),
                AgentLpqCoordinator.stageCombatTargets(9, java.util.Set.of()));
        assertEquals(java.util.Set.of(9_300_001),
                AgentLpqCoordinator.stageCombatTargets(5, java.util.Set.of(9_300_001)));
    }

    @Test
    void bonusLootSelectionIncludesEveryUnclaimedItemAndMesoDrop() {
        MapItem item = mock(MapItem.class);
        when(item.getItemId()).thenReturn(2_000_003);
        MapItem meso = mock(MapItem.class);
        when(meso.getItemId()).thenReturn(777);
        MapItem picked = mock(MapItem.class);
        when(picked.getItemId()).thenReturn(4_000_001);
        when(picked.isPickedUp()).thenReturn(true);

        assertEquals(Set.of(2_000_003, 777),
                AgentLpqCoordinator.bonusDropIds(List.of(item, meso, picked)));
        assertEquals(Set.of(), AgentLpqCoordinator.bonusDropIds(List.of()));
    }

    private static Reactor reactor(int objectId, Point position) {
        Reactor reactor = mock(Reactor.class);
        when(reactor.getObjectId()).thenReturn(objectId);
        when(reactor.getPosition()).thenReturn(position);
        return reactor;
    }
}
