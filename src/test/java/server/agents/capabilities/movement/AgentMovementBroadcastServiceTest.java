package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentMovementBroadcastServiceTest {
    @Test
    void ropeClimbingBroadcastsCapturedSentinelWithoutReplacingCachedGround() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(10, 20, 30, false));

        assertEquals(-2, AgentMovementBroadcastService.resolveBroadcastFhId(entry, agent));
        assertEquals(12345, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }

    @Test
    void ladderClimbingRetainsPreviousGroundFootholdLayer() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(10, 20, 30, true));

        assertEquals(12345, AgentMovementBroadcastService.resolveBroadcastFhId(entry, agent));
        assertEquals(12345, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }

    @Test
    void lithHarborForegroundLadderUsesCapturedRopeRenderLayer() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(2_114, 289, 485, true));

        assertEquals(-2, AgentMovementBroadcastService.resolveBroadcastFhId(entry, agent));
        assertEquals(12345, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));
    }

    @Test
    void lithHarborBackgroundLadderUsesCapturedRopeRenderLayer() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(2_151, 499, 645, true));

        assertEquals(-2, AgentMovementBroadcastService.resolveBroadcastFhId(entry, agent));
    }

    @Test
    void airborneAfterRopeDetachBroadcastsZeroWithoutResolvingGroundBelow() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, 12345);
        AgentMovementStateRuntime.setInAir(entry, true);

        assertEquals(0, AgentMovementBroadcastService.resolveBroadcastFhId(entry, agent));
        assertEquals(12345, AgentMovementPhysicsStateRuntime.lastGroundFhId(entry));
        verifyNoInteractions(agent);
    }

    @Test
    void unobservedMapSkipsPacketWorkAndInvalidatesDedupState() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(false);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 1, 2, 3, 4);

        AgentMovementBroadcastService.broadcastMovement(entry);

        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
    }
}
