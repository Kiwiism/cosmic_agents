package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotRetreatHoldStateRuntime;
import server.bots.BotEntry;

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
        BotEntry entry = new BotEntry(character(200, 100000000), leader, null);
        AgentBotModeStateRuntime.setGrinding(entry, true);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), true);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(30, 40), 100000000);

        AgentModeService.startFollow(entry, target);

        assertTrue(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(target.getId(), AgentBotModeStateRuntime.followTargetId(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
    }

    @Test
    void startFollowUsesLeaderWhenTargetIsLeaderOrMissing() {
        Character leader = character(100, 100000000);
        BotEntry entry = new BotEntry(character(200, 100000000), leader, null);

        AgentModeService.startFollow(entry, leader);
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));

        AgentModeService.startFollow(entry, null);
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));
    }

    @Test
    void startGrindAppliesActiveModeResetAndClearsNavigation() {
        BotEntry entry = dirtyEntry();
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startGrind(entry, ignored -> navigationClears.incrementAndGet());

        assertActiveModeReset(entry);
        assertEquals(1, navigationClears.get());
    }

    @Test
    void startStopClearsMovementAndExplicitMoveTargetButDoesNotClearNavigationCallback() {
        BotEntry entry = dirtyEntry();

        AgentModeService.startStop(entry);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertFalse(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));
    }

    @Test
    void startMoveToClearsModeAndSetsTarget() {
        BotEntry entry = dirtyEntry();
        Point destination = new Point(400, 500);

        AgentModeService.startMoveTo(entry, destination, true);

        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertFalse(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(destination, AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.isPrecise(entry));
    }

    @Test
    void startFarmHereUsesActiveModeAndSetsAnchorAndPreciseMoveTarget() {
        BotEntry entry = dirtyEntry();
        Point destination = new Point(700, 800);
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startFarmHere(entry, destination, ignored -> navigationClears.incrementAndGet());

        assertTrue(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(destination, AgentBotFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(123456789, AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertEquals(destination, AgentBotMoveTargetStateRuntime.moveTarget(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.isPrecise(entry));
        assertEquals(1, navigationClears.get());
    }

    @Test
    void startPatrolUsesActiveModeAndSetsPatrolRegion() {
        BotEntry entry = dirtyEntry();
        AtomicInteger navigationClears = new AtomicInteger();

        AgentModeService.startPatrol(entry, 42, ignored -> navigationClears.incrementAndGet());

        assertTrue(AgentBotModeStateRuntime.grinding(entry));
        assertTrue(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(42, AgentBotPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(123456789, AgentBotPatrolStateRuntime.patrolMapId(entry));
        assertEquals(1, navigationClears.get());
    }

    private static void assertActiveModeReset(BotEntry entry) {
        assertFalse(AgentBotModeStateRuntime.following(entry));
        assertTrue(AgentBotModeStateRuntime.grinding(entry));
        assertEquals(0, AgentBotModeStateRuntime.followTargetId(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
        assertFalse(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertFalse(AgentBotCombatCooldownStateRuntime.hasMoveWindow(entry));
        assertFalse(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));
        assertFalse(AgentBotRetreatHoldStateRuntime.hasHold(entry));
        assertEquals(0, AgentBotGrindWanderStateRuntime.wanderDirection(entry));
    }

    private static BotEntry dirtyEntry() {
        Character leader = character(100, 123456789);
        BotEntry entry = new BotEntry(character(200, 123456789), leader, null);
        AgentBotModeStateRuntime.startFollowing(entry, leader.getId());
        AgentBotModeStateRuntime.setGrinding(entry, true);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(10, 20), false);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(30, 40), 123456789);
        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 123456789);
        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, mock(server.maps.MapItem.class));
        AgentBotCombatCooldownStateRuntime.setMoveWindowMs(entry, 500);
        AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        AgentBotRetreatHoldStateRuntime.setHold(entry, new Point(50, 60), 9999L);
        AgentBotGrindWanderStateRuntime.setWanderDirection(entry, -1);
        return entry;
    }

    private static Character character(int id, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }
}
