package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentQueuedMovementActionServiceTest {
    @Test
    void queueDownJumpPreservesLegacyCrouchAndPendingState() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentQueuedMovementActionService.queueDownJump(entry, agent);

        assertTrue(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertTrue(AgentBotMovementStateRuntime.crouching(entry));
    }

    @Test
    void beginDownJumpClearsPendingWhenNoValidGroundAllowsDrop() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, true);

        AgentQueuedMovementActionService.beginDownJump(entry, agent);

        assertFalse(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));
        assertEquals(0L, AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry));
    }

    @Test
    void beginDownJumpStartsAirborneLaunchWithLegacyKickAndGracePeriod() {
        MapleMap map = mock(MapleMap.class);
        FootholdTree footholds = new FootholdTree(new Point(-1000, -1000), new Point(1000, 1000));
        Foothold ground = new Foothold(new Point(0, 100), new Point(200, 100), 1);
        footholds.insert(ground);
        when(map.getFootholds()).thenReturn(footholds);
        when(map.getPointBelow(new Point(50, 100))).thenReturn(new Point(50, 100));
        when(map.getPointBelow(new Point(50, 100 - AgentMovementPhysicsConfig.configuredMaxSlopeUp())))
                .thenReturn(new Point(50, 100));

        Character agent = mock(Character.class);
        Point position = new Point(50, 100);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(position);
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, true);

        AgentQueuedMovementActionService.beginDownJump(entry, agent);

        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertFalse(AgentBotMovementStateRuntime.downJumpPending(entry));
        assertFalse(AgentBotMovementStateRuntime.crouching(entry));
        assertEquals(-AgentAirborneLaunchService.downJumpForcePerTick(),
                AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry), 0.0001f);
        assertEquals(AgentMovementPhysicsConfig.configuredDownJumpGraceMs(),
                AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry));
    }
}
