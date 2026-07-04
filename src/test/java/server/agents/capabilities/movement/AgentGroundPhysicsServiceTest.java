package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGroundPhysicsServiceTest {
    @Test
    void stopGroundMotionPreservesLegacyVelocityReset() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        BotEntry entry = new BotEntry(agent, null, null);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 2.5);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 7, 3);

        AgentGroundPhysicsService.stopGroundMotion(entry);

        assertEquals(0.0, AgentBotMovementPhysicsStateRuntime.horizontalSpeed(entry));
        assertEquals(7, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(3, AgentBotMovementStateRuntime.movementVelocityY(entry));
    }

    @Test
    void velocityFromDeltaXUsesLegacyPacketScaling() {
        assertEquals(160, AgentGroundPhysicsService.velocityFromDeltaX(8));
        assertEquals(-160, AgentGroundPhysicsService.velocityFromDeltaX(-8));
        assertEquals(0, AgentGroundPhysicsService.velocityFromDeltaX(0));
    }

    @Test
    void applyGroundMotionPreservesLegacyGroundStepAndPacketVelocity() {
        MapleMap map = createEmptyTestMap(910000301);
        Foothold foothold = new Foothold(new Point(0, 100), new Point(400, 100), 1);
        map.getFootholds().insert(foothold);
        Character agent = mockAgent(new Point(100, 100), map);
        BotEntry entry = new BotEntry(agent, null, null);
        AgentBotMovementPhysicsStateRuntime.setPhysicsX(entry, 100);
        AgentBotMovementStateRuntime.setMoveDirection(entry, 1);

        AgentGroundMotion motion = AgentGroundPhysicsService.applyGroundMotion(entry, agent, foothold);

        assertFalse(motion.lostGround());
        assertTrue(motion.stepX() > 0);
        assertTrue(AgentBotMovementStateRuntime.movementVelocityX(entry) > 0);
        assertEquals(0, AgentBotMovementStateRuntime.movementVelocityY(entry));
    }

    @Test
    void syncAndDetectGroundStartsFallWhenNoGroundExists() {
        MapleMap map = createEmptyTestMap(910000302);
        Character agent = mockAgent(new Point(100, 100), map);
        BotEntry entry = new BotEntry(agent, null, null);

        Foothold foothold = AgentGroundPhysicsService.syncAndDetectGround(entry, agent);

        assertEquals(null, foothold);
        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        verify(agent).setPosition(new Point(100, 100));
    }

    private static MapleMap createEmptyTestMap(int mapId) {
        MapleMap map = new MapleMap(mapId, 0, 0, mapId, 1.0f);
        map.setFootholds(new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        return map;
    }

    private static Character mockAgent(Point startPosition, MapleMap map) {
        Character agent = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        when(agent.getPosition()).thenAnswer(ignored -> new Point(position.get()));
        when(agent.getMap()).thenReturn(map);
        when(agent.getHp()).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(agent).setPosition(org.mockito.ArgumentMatchers.any(Point.class));
        return agent;
    }
}
