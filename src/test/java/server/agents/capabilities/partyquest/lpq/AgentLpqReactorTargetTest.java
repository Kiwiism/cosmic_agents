package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import server.maps.Reactor;
import server.maps.MapItem;

import java.awt.Point;
import java.util.List;
import java.util.Set;

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

        assertNull(AgentLpqCoordinator.selectCommittedReactor(
                member, 922_010_502, new Point(100, 0), List.of(second), true));
        assertTrue(member.reactorSpawnCleanupPending());
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
        assertFalse(member.reactorSpawnCleanupPending());

        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 70_006, 5, 1_000L);
        session.addMember(71_006, AgentLpqMemberState.MemberType.AGENT);
        session.member(71_006).commitReactorTarget(922_010_502, 202);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);
        assertEquals(0, session.member(71_006).reactorTargetObjectId());
    }

    @Test
    void reactorRecoveryClockSpansEveryTargetInOneRoomAndClearsOnExit() {
        AgentLpqMemberState member = new AgentLpqMemberState(
                71_007, AgentLpqMemberState.MemberType.AGENT);
        member.assign(AgentLpqMemberState.Role.GENERAL, 922_010_503);
        member.commitReactorTarget(922_010_503, 301, 10_000L);
        member.markReactorTargetBroken(false);
        member.commitReactorTarget(922_010_503, 302, 80_000L);

        assertEquals(10_000L, member.reactorTargetCommittedAtMs());

        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        assertEquals(0L, member.reactorTargetCommittedAtMs());
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
