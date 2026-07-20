package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.social.airshow.AgentAirshowStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementSettleServiceTest {
    @Test
    void settlesOnlyUnreconciledGroundedLiveAgentWhoseLastPacketWasMoving() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(mock(MapleMap.class));
        when(agent.getHp()).thenReturn(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 50, 0, 1, 2);

        AgentMovementSettleService.beginTick(entry);

        assertTrue(AgentMovementSettleService.shouldSettle(entry));
        AgentMovementBroadcastStateRuntime.markReconciled(entry);
        assertFalse(AgentMovementSettleService.shouldSettle(entry));
    }

    @Test
    void skipsAirborneClimbingAirshowAndAlreadyIdleStates() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(mock(MapleMap.class));
        when(agent.getHp()).thenReturn(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 0, 0, 0, 2);
        assertFalse(AgentMovementSettleService.shouldSettle(entry));

        AgentMovementBroadcastStateRuntime.record(
                entry, 10, 20, 0, 0, CharacterStance.WALK_RIGHT_STANCE, 2);
        assertTrue(AgentMovementSettleService.shouldSettle(entry));

        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 50, 0, 1, 2);
        AgentMovementStateRuntime.setInAir(entry, true);
        assertFalse(AgentMovementSettleService.shouldSettle(entry));
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentAirshowStateRuntime.start(entry);
        assertFalse(AgentMovementSettleService.shouldSettle(entry));
    }
}
