package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotShopStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRecoveryTeleportServiceTest {
    @Test
    void recoversWhenTargetIsBeyondTeleportDistance() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(0, 0));
        BotEntry entry = entry(agent);
        Counters counters = new Counters(new Point(99, 100));

        boolean recovered = AgentRecoveryTeleportService.recoverTeleportDistance(
                entry, agent, new Point(100, 100), 50, 20, hooks(counters));

        assertTrue(recovered);
        assertEquals(new Point(100, 99), counters.groundQuery.get());
        assertEquals(new Point(99, 100), counters.teleportPoint.get());
        counters.assertRecoverySideEffects();
    }

    @Test
    void recoversWhenOutOfBoundsPastOutOfBoundsDistance() {
        MapleMap map = map(new Rectangle(0, 0, 100, 100));
        Character agent = character(map, new Point(150, 10));
        BotEntry entry = entry(agent);
        Counters counters = new Counters(null);

        boolean recovered = AgentRecoveryTeleportService.recoverTeleportDistance(
                entry, agent, new Point(120, 10), 1000, 20, hooks(counters));

        assertTrue(recovered);
        assertEquals(new Point(120, 10), counters.teleportPoint.get());
        counters.assertRecoverySideEffects();
    }

    @Test
    void doesNotRecoverWhenWithinDistanceAndInBounds() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(10, 10));
        BotEntry entry = entry(agent);
        Counters counters = new Counters(null);

        boolean recovered = AgentRecoveryTeleportService.recoverTeleportDistance(
                entry, agent, new Point(20, 10), 1000, 20, hooks(counters));

        assertFalse(recovered);
        counters.assertNoSideEffects();
    }

    @Test
    void grindPartyRecoveryRequiresGrindingAndSameMap() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(0, 0));
        Character anchor = character(map, new Point(500, 0));
        BotEntry entry = entry(agent);
        Counters counters = new Counters(null);

        boolean recovered = AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry, agent, anchor, 100, 20, 1, hooks(counters));

        assertFalse(recovered);
        counters.assertNoSideEffects();
    }

    @Test
    void grindPartyRecoverySkipsWhenExplicitMoveOrFarmAnchorIsActive() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(0, 0));
        Character anchor = character(map, new Point(500, 0));
        BotEntry entry = entry(agent);
        AgentBotModeStateRuntime.startGrinding(entry);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(5, 5), false);
        Counters counters = new Counters(null);

        assertFalse(AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry, agent, anchor, 100, 20, 1, hooks(counters)));
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(10, 10), map.getId());

        assertFalse(AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry, agent, anchor, 100, 20, 1, hooks(counters)));
        counters.assertNoSideEffects();
    }

    @Test
    void grindPartyRecoveryUsesMultiplierAndAnchorPosition() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(0, 0));
        Character anchor = character(map, new Point(500, 0));
        BotEntry entry = entry(agent);
        AgentBotModeStateRuntime.startGrinding(entry);
        Counters counters = new Counters(null);

        boolean recovered = AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry, agent, anchor, 100, 20, 2, hooks(counters));

        assertTrue(recovered);
        assertEquals(new Point(500, 0), counters.teleportPoint.get());
        counters.assertRecoverySideEffects();
    }

    @Test
    void grindPartyRecoverySkipsDuringShopVisit() {
        MapleMap map = map(new Rectangle(0, 0, 1000, 1000));
        Character agent = character(map, new Point(0, 0));
        Character anchor = character(map, new Point(500, 0));
        BotEntry entry = entry(agent);
        AgentBotModeStateRuntime.startGrinding(entry);
        AgentBotShopStateRuntime.startShopVisit(entry, new Point(1, 1), new Point(1, 1), 0, 1L);
        Counters counters = new Counters(null);

        boolean recovered = AgentRecoveryTeleportService.recoverGrindPartyTeleportDistance(
                entry, agent, anchor, 100, 20, 1, hooks(counters));

        assertFalse(recovered);
        counters.assertNoSideEffects();
    }

    private static AgentRecoveryTeleportService.RecoveryHooks hooks(Counters counters) {
        return new AgentRecoveryTeleportService.RecoveryHooks(
                (map, point) -> {
                    counters.groundQueries.incrementAndGet();
                    counters.groundQuery.set(new Point(point));
                    return counters.groundPoint;
                },
                (entry, agent, point) -> {
                    counters.teleports.incrementAndGet();
                    counters.teleportPoint.set(new Point(point));
                },
                entry -> counters.resets.incrementAndGet(),
                entry -> counters.broadcasts.incrementAndGet());
    }

    private static BotEntry entry(Character agent) {
        return new BotEntry(agent, mock(Character.class), null);
    }

    private static Character character(MapleMap map, Point position) {
        Character character = mock(Character.class);
        when(character.getMap()).thenReturn(map);
        when(character.getPosition()).thenReturn(new Point(position));
        return character;
    }

    private static MapleMap map(Rectangle area) {
        MapleMap map = mock(MapleMap.class);
        when(map.getMapArea()).thenReturn(area);
        when(map.getId()).thenReturn(100000000);
        return map;
    }

    private static final class Counters {
        private final Point groundPoint;
        private final AtomicInteger groundQueries = new AtomicInteger();
        private final AtomicInteger teleports = new AtomicInteger();
        private final AtomicInteger resets = new AtomicInteger();
        private final AtomicInteger broadcasts = new AtomicInteger();
        private final AtomicReference<Point> groundQuery = new AtomicReference<>();
        private final AtomicReference<Point> teleportPoint = new AtomicReference<>();

        private Counters(Point groundPoint) {
            this.groundPoint = groundPoint;
        }

        private void assertRecoverySideEffects() {
            assertEquals(1, groundQueries.get());
            assertEquals(1, teleports.get());
            assertEquals(1, resets.get());
            assertEquals(1, broadcasts.get());
        }

        private void assertNoSideEffects() {
            assertEquals(0, groundQueries.get());
            assertEquals(0, teleports.get());
            assertEquals(0, resets.get());
            assertEquals(0, broadcasts.get());
        }
    }
}
