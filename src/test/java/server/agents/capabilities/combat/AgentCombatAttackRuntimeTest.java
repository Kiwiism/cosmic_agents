package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatAttackRuntimeTest {
    @Test
    void blocksAttacksUntilMapTransitionAndWarmupAreComplete() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);

        when(map.hasTransitioningPlayerObserver()).thenReturn(true);
        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(false);
        assertFalse(AgentAttackExecutionProvider.mapReadyForAttack(agent));

        when(map.hasTransitioningPlayerObserver()).thenReturn(false);
        assertFalse(AgentAttackExecutionProvider.mapReadyForAttack(agent));

        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(true);
        assertTrue(AgentAttackExecutionProvider.mapReadyForAttack(agent));
    }
}
