package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentActivityStateRuntime;
import server.agents.integration.AgentBuffStateRuntime;
import server.agents.integration.AgentDegenerateAttackStateRuntime;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentScriptTaskStateRuntime;
import server.agents.plans.AgentTask;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLeaderSafetyServiceTest {
    @Test
    void ownsTownClusterAnchorStore() {
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
        Point anchor = new Point(10, 20);

        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().put(123, anchor);

        assertEquals(anchor, AgentLeaderSafetyService.townClusterAnchorsByLeaderId().get(123));
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
    }

    @Test
    void doesNotTownWarpWhenMapIsMissing() {
        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(null));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(null));
    }

    @Test
    void doesNotTownWarpWhenReturnMapIsMissing() {
        MapleMap map = map(100, null, livingMonster());

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void doesNotTownWarpWhenReturnMapIsSameMap() {
        MapleMap map = map(100, null, livingMonster());
        when(map.getReturnMap()).thenReturn(map);

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertFalse(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void doesNotTownWarpWhenNoAliveMonstersArePresent() {
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap, deadMonster());

        assertFalse(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertTrue(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void townWarpsOnlyWhenAliveMonsterAndDifferentReturnMapExist() {
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap, deadMonster(), livingMonster());

        assertTrue(AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(map));
        assertTrue(AgentLeaderSafetyService.canReturnToDifferentMap(map));
    }

    @Test
    void preparesInactiveIdleWithLegacyResetOrderAndState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        AgentGrindTargetStateRuntime.setTarget(entry, livingMonster());
        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        AgentBuffStateRuntime.setEnabled(entry, true);
        AgentScriptTaskStateRuntime.queueTask(entry, AgentTask.stop());
        AtomicInteger order = new AtomicInteger();

        AgentLeaderSafetyService.prepareInactiveIdle(
                entry,
                () -> {
                    assertEquals(0, order.getAndIncrement());
                    AgentScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);
                },
                () -> assertEquals(1, order.getAndIncrement()),
                () -> assertEquals(2, order.getAndIncrement()));

        assertEquals(3, order.get());
        assertFalse(AgentScriptTaskStateRuntime.hasQueuedTasks(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertNull(AgentGrindTargetStateRuntime.target(entry));
        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));
        assertFalse(AgentBuffStateRuntime.enabled(entry));
        assertTrue(AgentActivityStateRuntime.ownerAwaySafeMode(entry));
    }

    @Test
    void activeLeaderReturnDoesNothingForAwaySafeModeWithoutTimer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.setOwnerAwaySafeMode(entry, true);
        Counters counters = new Counters();

        AgentLeaderSafetyService.handleActiveLeaderReturn(
                entry, () -> clearMoveTarget(entry, counters), counters::removeAnchor, counters::announce);

        counters.assertCounts(0, 0, 0);
        assertTrue(AgentActivityStateRuntime.ownerAwaySafeMode(entry));
    }

    @Test
    void activeLeaderReturnClearsTimerAndMoveTargetWithoutAnnouncementWhenNotReturnedToTown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.startOwnerInactiveTimer(entry, 1_000L);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        Counters counters = new Counters();

        AgentLeaderSafetyService.handleActiveLeaderReturn(
                entry, () -> clearMoveTarget(entry, counters), counters::removeAnchor, counters::announce);

        counters.assertCounts(1, 1, 0);
        assertFalse(AgentActivityStateRuntime.ownerInactiveTimerStarted(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void activeLeaderReturnAnnouncesOnlyWhenReturnedToTownAnchorWasRemoved() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        Counters counters = new Counters();
        counters.anchor.set(new Point(50, 60));

        AgentLeaderSafetyService.handleActiveLeaderReturn(
                entry, () -> clearMoveTarget(entry, counters), counters::removeAnchor, counters::announce);

        counters.assertCounts(1, 1, 1);
        assertFalse(AgentActivityStateRuntime.ownerReturnedToTown(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void inactiveLeaderStartsTimerAndDoesNotEnterSafeModeImmediately() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        boolean enterSafeMode = AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, 1_000L, 5_000L);

        assertFalse(enterSafeMode);
        assertEquals(1_000L, AgentActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
    }

    @Test
    void inactiveLeaderWaitsUntilConfiguredDelayElapses() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.startOwnerInactiveTimer(entry, 1_000L);

        assertFalse(AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, 5_999L, 5_000L));
        assertTrue(AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, 6_000L, 5_000L));
    }

    @Test
    void activeLeaderTickRunsReturnHandlerAndDoesNotConsumeTick() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character leader = mock(Character.class);
        when(leader.getHp()).thenReturn(100);
        AtomicInteger returns = new AtomicInteger();
        AtomicInteger safeModes = new AtomicInteger();

        boolean consumed = AgentLeaderSafetyService.handleInactiveLeaderTick(
                entry,
                leader,
                1_000L,
                new AgentLeaderSafetyService.InactiveLeaderTickHooks(
                        returnedEntry -> returns.incrementAndGet(),
                        inactiveEntry -> {
                            throw new AssertionError("town policy should not run for active leader");
                        },
                        (inactiveEntry, town) -> {
                            safeModes.incrementAndGet();
                            return true;
                        },
                        5_000L));

        assertFalse(consumed);
        assertEquals(1, returns.get());
        assertEquals(0, safeModes.get());
    }

    @Test
    void inactiveLeaderTickStartsTimerAndWaits() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger safeModes = new AtomicInteger();

        boolean consumed = AgentLeaderSafetyService.handleInactiveLeaderTick(
                entry,
                null,
                1_000L,
                new AgentLeaderSafetyService.InactiveLeaderTickHooks(
                        returnedEntry -> {
                            throw new AssertionError("return handler should not run for inactive leader");
                        },
                        inactiveEntry -> true,
                        (inactiveEntry, town) -> {
                            safeModes.incrementAndGet();
                            return true;
                        },
                        5_000L));

        assertFalse(consumed);
        assertEquals(0, safeModes.get());
        assertEquals(1_000L, AgentActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
    }

    @Test
    void inactiveLeaderTickEntersSafeModeAfterDelayWithTownDecision() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character leader = mock(Character.class);
        when(leader.getHp()).thenReturn(0);
        AgentActivityStateRuntime.startOwnerInactiveTimer(entry, 1_000L);
        AtomicReference<Boolean> townDecisionSeen = new AtomicReference<>();

        boolean consumed = AgentLeaderSafetyService.handleInactiveLeaderTick(
                entry,
                leader,
                6_000L,
                new AgentLeaderSafetyService.InactiveLeaderTickHooks(
                        returnedEntry -> {
                            throw new AssertionError("return handler should not run for inactive leader");
                        },
                        inactiveEntry -> true,
                        (inactiveEntry, town) -> {
                            townDecisionSeen.set(town);
                            return town;
                        },
                        5_000L));

        assertTrue(consumed);
        assertEquals(Boolean.TRUE, townDecisionSeen.get());
    }

    @Test
    void returnedToTownAwaySafeModeStartsTimerButDoesNotReenterSafeMode() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.setOwnerReturnedToTown(entry, true);
        AgentActivityStateRuntime.setOwnerAwaySafeMode(entry, true);

        boolean enterSafeMode = AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, 2_000L, 5_000L);

        assertFalse(enterSafeMode);
        assertEquals(2_000L, AgentActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry));
    }

    @Test
    void returnedToTownWithoutAwaySafeModeDoesNotStartTimer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentActivityStateRuntime.setOwnerReturnedToTown(entry, true);

        boolean enterSafeMode = AgentLeaderSafetyService.shouldEnterInactiveSafeMode(entry, 2_000L, 5_000L);

        assertFalse(enterSafeMode);
        assertFalse(AgentActivityStateRuntime.ownerInactiveTimerStarted(entry));
    }

    @Test
    void inactiveAgentIdleInPlaceRunsPhysicsThenBroadcastAndMarksReturnedToTown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger order = new AtomicInteger();

        AgentLeaderSafetyService.idleInactiveAgentInPlace(
                entry,
                () -> assertEquals(0, order.getAndIncrement()),
                () -> assertEquals(1, order.getAndIncrement()));

        assertEquals(2, order.get());
        assertTrue(AgentActivityStateRuntime.ownerReturnedToTown(entry));
    }

    @Test
    void townClusterTargetReturnsAnchorWhenAgentOrMapIsMissing() {
        Point anchor = new Point(40, 80);
        Point target = AgentLeaderSafetyService.resolveTownClusterTarget(
                null,
                null,
                anchor,
                List.of(),
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STACK, 0, 0),
                12,
                (map, point) -> {
                    throw new AssertionError("ground lookup should not run");
                });

        assertEquals(anchor, target);
    }

    @Test
    void townClusterTargetAppliesFormationAndClampsToMapAreaBeforeGroundLookup() {
        MapleMap map = mapWithArea(100, new java.awt.Rectangle(0, 0, 100, 200));
        AgentRuntimeEntry other = entryAt(new Point(10, 80), map, 100);
        AgentRuntimeEntry entry = entryAt(new Point(20, 80), map, 100);
        AtomicReference<Point> query = new AtomicReference<>();

        Point target = AgentLeaderSafetyService.resolveTownClusterTarget(
                entry,
                map,
                new Point(50, 80),
                List.of(other, entry),
                new AgentFormationService.FormationState(AgentFormationService.FormationType.RIGHT, 30, 0),
                12,
                (ignoredMap, point) -> {
                    query.set(new Point(point));
                    return new Point(point.x, 90);
                });

        assertEquals(new Point(100, 79), query.get());
        assertEquals(new Point(100, 90), target);
    }

    @Test
    void townClusterTargetFallsBackToAnchorGroundThenBase() {
        MapleMap map = mapWithArea(100, new java.awt.Rectangle(0, 0, 100, 200));
        AgentRuntimeEntry entry = entryAt(new Point(20, 80), map, 100);
        AtomicInteger calls = new AtomicInteger();

        Point target = AgentLeaderSafetyService.resolveTownClusterTarget(
                entry,
                map,
                new Point(50, 80),
                List.of(entry),
                new AgentFormationService.FormationState(AgentFormationService.FormationType.RIGHT, 30, 0),
                12,
                (ignoredMap, point) -> calls.getAndIncrement() == 0 ? null : new Point(point.x, 91));

        assertEquals(new Point(50, 91), target);
        assertEquals(2, calls.get());
    }

    @Test
    void markInactiveTownReturnHandledSetsReturnedToTown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        AgentLeaderSafetyService.markInactiveTownReturnHandled(entry);

        assertTrue(AgentActivityStateRuntime.ownerReturnedToTown(entry));
    }

    @Test
    void startInactiveTownClusterMoveResetsThenStartsMoveThenMarksReturnedToTown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger order = new AtomicInteger();

        AgentLeaderSafetyService.startInactiveTownClusterMove(
                entry,
                () -> assertEquals(0, order.getAndIncrement()),
                () -> assertEquals(1, order.getAndIncrement()));

        assertEquals(2, order.get());
        assertTrue(AgentActivityStateRuntime.ownerReturnedToTown(entry));
    }

    @Test
    void enterInactiveSafeModePreparesThenRunsTownScrollAndReturnsResult() {
        AtomicInteger order = new AtomicInteger();

        boolean consumed = AgentLeaderSafetyService.enterInactiveSafeMode(
                () -> assertEquals(0, order.getAndIncrement()),
                true,
                () -> {
                    assertEquals(1, order.getAndIncrement());
                    return true;
                },
                () -> {
                    throw new AssertionError("idle branch should not run");
                });

        assertTrue(consumed);
        assertEquals(2, order.get());
    }

    @Test
    void enterInactiveSafeModePreparesThenRunsIdleBranchAndReturnsFalse() {
        AtomicInteger order = new AtomicInteger();

        boolean consumed = AgentLeaderSafetyService.enterInactiveSafeMode(
                () -> assertEquals(0, order.getAndIncrement()),
                false,
                () -> {
                    throw new AssertionError("town branch should not run");
                },
                () -> assertEquals(1, order.getAndIncrement()));

        assertFalse(consumed);
        assertEquals(2, order.get());
    }

    @Test
    void scrollInactiveAgentToTownReturnsFalseWhenCurrentMapIsMissing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        TownScrollCounters counters = new TownScrollCounters();

        boolean consumed = AgentLeaderSafetyService.scrollInactiveAgentToTown(entry, new AgentLeaderSafetyService.TownScrollHooks(
                () -> null,
                counters::markReturnHandled,
                counters::idleOnGround,
                () -> true,
                ignored -> counters.changeMap(),
                counters::groundAfterMapChange,
                () -> new Point(10, 20),
                counters::putAnchor,
                counters::resolveTarget,
                counters::resetEntryState,
                counters::startMove));

        assertFalse(consumed);
        counters.assertCounts(0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void scrollInactiveAgentToTownMarksHandledWhenReturnMapIsMissing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        MapleMap map = map(100, null);
        TownScrollCounters counters = new TownScrollCounters();

        boolean consumed = AgentLeaderSafetyService.scrollInactiveAgentToTown(entry, townHooks(map, counters, true));

        assertFalse(consumed);
        counters.assertCounts(1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void scrollInactiveAgentToTownUsesReturnScrollWhenAvailableAndStartsClusterMove() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap);
        TownScrollCounters counters = new TownScrollCounters();

        boolean consumed = AgentLeaderSafetyService.scrollInactiveAgentToTown(entry, townHooks(map, counters, true));

        assertTrue(consumed);
        counters.assertCounts(0, 1, 0, 1, 1, 1, 1);
        assertEquals(new Point(31, 41), counters.startedMove.get());
    }

    @Test
    void scrollInactiveAgentToTownChangesMapWhenReturnScrollIsUnavailable() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        MapleMap returnMap = map(200, null);
        MapleMap map = map(100, returnMap);
        TownScrollCounters counters = new TownScrollCounters();

        boolean consumed = AgentLeaderSafetyService.scrollInactiveAgentToTown(entry, townHooks(map, counters, false));

        assertTrue(consumed);
        counters.assertCounts(0, 1, 1, 1, 1, 1, 1);
    }

    @Test
    void issueInactiveSafeModeForLeaderSkipsEntriesWithoutMaps() {
        AgentRuntimeEntry skipped = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentRuntimeEntry handled = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger handledCount = new AtomicInteger();

        AgentLeaderSafetyService.issueInactiveSafeModeForLeader(
                List.of(skipped, handled),
                true,
                entry -> entry == handled,
                entry -> true,
                (entry, town) -> {
                    assertEquals(handled, entry);
                    assertTrue(town);
                    handledCount.incrementAndGet();
                });

        assertEquals(1, handledCount.get());
    }

    @Test
    void issueInactiveSafeModeForLeaderCombinesTownRequestWithEligibility() {
        AgentRuntimeEntry eligible = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentRuntimeEntry ineligible = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Map<AgentRuntimeEntry, Boolean> eligibility = Map.of(eligible, true, ineligible, false);
        AtomicInteger townEntries = new AtomicInteger();
        AtomicInteger idleEntries = new AtomicInteger();

        AgentLeaderSafetyService.issueInactiveSafeModeForLeader(
                List.of(eligible, ineligible),
                true,
                entry -> true,
                eligibility::get,
                (entry, town) -> {
                    if (town) {
                        townEntries.incrementAndGet();
                    } else {
                        idleEntries.incrementAndGet();
                    }
                });

        assertEquals(1, townEntries.get());
        assertEquals(1, idleEntries.get());
    }

    @Test
    void issueInactiveSafeModeForLeaderNeverTownsWhenTownWasNotRequested() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger idleEntries = new AtomicInteger();

        AgentLeaderSafetyService.issueInactiveSafeModeForLeader(
                List.of(entry),
                false,
                ignored -> true,
                ignored -> {
                    throw new AssertionError("town eligibility should not matter when town was not requested");
                },
                (ignoredEntry, town) -> {
                    assertFalse(town);
                    idleEntries.incrementAndGet();
                });

        assertEquals(1, idleEntries.get());
    }

    private static MapleMap map(int id, MapleMap returnMap, Monster... monsters) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getReturnMap()).thenReturn(returnMap);
        when(map.getAllMonsters()).thenReturn(List.of(monsters));
        return map;
    }

    private static MapleMap mapWithArea(int id, java.awt.Rectangle area) {
        MapleMap map = mock(MapleMap.class);
        when(map.getId()).thenReturn(id);
        when(map.getMapArea()).thenReturn(area);
        when(map.getFootholds()).thenReturn(null);
        return map;
    }

    private static AgentRuntimeEntry entryAt(Point position, MapleMap map, int mapId) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(position));
        when(agent.getMap()).thenReturn(map);
        when(agent.getMapId()).thenReturn(mapId);
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }

    private static AgentLeaderSafetyService.TownScrollHooks townHooks(
            MapleMap map, TownScrollCounters counters, boolean returnScrollAvailable) {
        return new AgentLeaderSafetyService.TownScrollHooks(
                () -> map,
                counters::markReturnHandled,
                counters::idleOnGround,
                () -> returnScrollAvailable,
                ignored -> counters.changeMap(),
                counters::groundAfterMapChange,
                () -> new Point(30, 40),
                counters::putAnchor,
                counters::resolveTarget,
                counters::resetEntryState,
                counters::startMove);
    }

    private static Monster livingMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isAlive()).thenReturn(true);
        return monster;
    }

    private static Monster deadMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isAlive()).thenReturn(false);
        return monster;
    }

    private static void clearMoveTarget(AgentRuntimeEntry entry, Counters counters) {
        counters.clearMoveTarget();
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
    }

    private static final class Counters {
        private final AtomicInteger moveTargetClears = new AtomicInteger();
        private final AtomicInteger anchorRemoves = new AtomicInteger();
        private final AtomicInteger announcements = new AtomicInteger();
        private final AtomicReference<Point> anchor = new AtomicReference<>();

        private void clearMoveTarget() {
            moveTargetClears.incrementAndGet();
        }

        private Point removeAnchor() {
            anchorRemoves.incrementAndGet();
            return anchor.getAndSet(null);
        }

        private void announce() {
            announcements.incrementAndGet();
        }

        private void assertCounts(int expectedMoveTargetClears, int expectedAnchorRemoves, int expectedAnnouncements) {
            assertEquals(expectedMoveTargetClears, moveTargetClears.get());
            assertEquals(expectedAnchorRemoves, anchorRemoves.get());
            assertEquals(expectedAnnouncements, announcements.get());
        }
    }

    private static final class TownScrollCounters {
        private final AtomicInteger returnHandled = new AtomicInteger();
        private final AtomicInteger idleOnGround = new AtomicInteger();
        private final AtomicInteger changeMap = new AtomicInteger();
        private final AtomicInteger groundAfterMapChange = new AtomicInteger();
        private final AtomicInteger anchors = new AtomicInteger();
        private final AtomicInteger resets = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicReference<Point> startedMove = new AtomicReference<>();

        private void markReturnHandled() {
            returnHandled.incrementAndGet();
        }

        private void idleOnGround() {
            idleOnGround.incrementAndGet();
        }

        private void changeMap() {
            changeMap.incrementAndGet();
        }

        private void groundAfterMapChange() {
            groundAfterMapChange.incrementAndGet();
        }

        private Point putAnchor(Point post) {
            anchors.incrementAndGet();
            return null;
        }

        private Point resolveTarget(MapleMap returnMap, Point anchor) {
            return new Point(anchor.x + 1, anchor.y + 1);
        }

        private void resetEntryState() {
            resets.incrementAndGet();
        }

        private void startMove(Point target) {
            starts.incrementAndGet();
            startedMove.set(new Point(target));
        }

        private void assertCounts(int expectedReturnHandled,
                                  int expectedIdleOnGround,
                                  int expectedChangeMap,
                                  int expectedGroundAfterMapChange,
                                  int expectedAnchors,
                                  int expectedResets,
                                  int expectedStarts) {
            assertEquals(expectedReturnHandled, returnHandled.get());
            assertEquals(expectedIdleOnGround, idleOnGround.get());
            assertEquals(expectedChangeMap, changeMap.get());
            assertEquals(expectedGroundAfterMapChange, groundAfterMapChange.get());
            assertEquals(expectedAnchors, anchors.get());
            assertEquals(expectedResets, resets.get());
            assertEquals(expectedStarts, starts.get());
        }
    }
}

