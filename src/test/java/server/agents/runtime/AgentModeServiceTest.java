package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentDegenerateAttackStateRuntime;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.integration.AgentGrindLootStateRuntime;
import server.agents.integration.AgentGrindWanderStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.capabilities.combat.AgentRetreatHoldStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentModeServiceTest {
    @Test
    void startFollowPreservesLegacyTargetAndClearsMovementModes() {
        Character leader = character(100, 100000000);
        Character target = character(300, 100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), leader, null);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(30, 40), 100000000);

        AgentModeService.startFollow(entry, target);

        assertTrue(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(target.getId(), AgentModeStateRuntime.followTargetId(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
    }

    @Test
    void startFollowUsesLeaderWhenTargetIsLeaderOrMissing() {
        Character leader = character(100, 100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 100000000), leader, null);

        AgentModeService.startFollow(entry, leader);
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));

        AgentModeService.startFollow(entry, null);
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));
    }

    @Test
    void startGrindAppliesActiveModeResetAndClearsNavigation() {
        AgentRuntimeEntry entry = dirtyEntry();
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startGrind(entry, ignored -> navigationClears.incrementAndGet());

        assertActiveModeReset(entry);
        assertEquals(1, navigationClears.get());
    }

    @Test
    void startStopClearsMovementAndExplicitMoveTargetButDoesNotClearNavigationCallback() {
        AgentRuntimeEntry entry = dirtyEntry();

        AgentModeService.startStop(entry);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertFalse(AgentGrindLootStateRuntime.hasGrindLootTarget(entry));
    }

    @Test
    void startMoveToClearsModeAndSetsTarget() {
        AgentRuntimeEntry entry = dirtyEntry();
        Point destination = new Point(400, 500);

        AgentModeService.startMoveTo(entry, destination, true);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(destination, AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
    }

    @Test
    void startFarmHereUsesActiveModeAndSetsAnchorAndPreciseMoveTarget() {
        AgentRuntimeEntry entry = dirtyEntry();
        Point destination = new Point(700, 800);
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startFarmHere(entry, destination, ignored -> navigationClears.incrementAndGet());

        assertTrue(AgentModeStateRuntime.grinding(entry));
        assertEquals(destination, AgentFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(123456789, AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertEquals(destination, AgentMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
        assertEquals(1, navigationClears.get());
    }

    @Test
    void startPatrolUsesActiveModeAndSetsPatrolRegion() {
        AgentRuntimeEntry entry = dirtyEntry();
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startPatrol(entry, 42, ignored -> navigationClears.incrementAndGet());

        assertTrue(AgentModeStateRuntime.grinding(entry));
        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(42, AgentPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(123456789, AgentPatrolStateRuntime.patrolMapId(entry));
        assertEquals(1, navigationClears.get());
    }

    private static void assertActiveModeReset(AgentRuntimeEntry entry) {
        assertFalse(AgentModeStateRuntime.following(entry));
        assertTrue(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertFalse(AgentGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertFalse(AgentCombatCooldownStateRuntime.hasMoveWindow(entry));
        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));
        assertFalse(AgentRetreatHoldStateRuntime.hasHold(entry));
        assertEquals(0, AgentGrindWanderStateRuntime.wanderDirection(entry));
    }

    private static AgentRuntimeEntry dirtyEntry() {
        Character leader = character(100, 123456789);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, 123456789), leader, null);
        AgentModeStateRuntime.startFollowing(entry, leader.getId());
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), false);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(30, 40), 123456789);
        AgentPatrolStateRuntime.startPatrol(entry, 7, 123456789);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, mock(server.maps.MapItem.class));
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry, 500);
        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        AgentRetreatHoldStateRuntime.setHold(entry, new Point(50, 60), 9999L);
        AgentGrindWanderStateRuntime.setWanderDirection(entry, -1);
        return entry;
    }

    private static Character character(int id, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }
}
