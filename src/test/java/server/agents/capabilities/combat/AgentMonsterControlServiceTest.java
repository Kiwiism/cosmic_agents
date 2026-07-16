package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMonsterControlServiceTest {
    @Test
    void releasesHiddenControllerWhenAgentSimulationLeavesMap() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character hiddenObserver = mock(Character.class);
        when(hiddenObserver.isHidden()).thenReturn(true);
        when(monster.getController()).thenReturn(hiddenObserver);
        when(map.getAllMonsters()).thenReturn(List.of(monster));
        when(map.shouldAllowHiddenMobSimulation(hiddenObserver)).thenReturn(false);

        AgentMonsterControlService.releaseHiddenSimulationControllers(map);

        verify(monster).aggroRedirectController();
    }

    @Test
    void keepsHiddenControllerWhileAnotherAgentStillNeedsSimulation() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character hiddenObserver = mock(Character.class);
        when(hiddenObserver.isHidden()).thenReturn(true);
        when(monster.getController()).thenReturn(hiddenObserver);
        when(map.getAllMonsters()).thenReturn(List.of(monster));
        when(map.shouldAllowHiddenMobSimulation(hiddenObserver)).thenReturn(true);

        AgentMonsterControlService.releaseHiddenSimulationControllers(map);

        verify(monster, never()).aggroRedirectController();
    }
}
