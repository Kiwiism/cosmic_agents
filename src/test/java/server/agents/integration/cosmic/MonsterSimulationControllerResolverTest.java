package server.agents.integration.cosmic;

import client.BotClient;
import client.Character;
import client.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.integration.AgentPresence;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonsterSimulationControllerResolverTest {
    @AfterEach
    void resetAgentPresence() {
        AgentPresence.install(null);
    }

    @Test
    void visiblePlayerActivatesAndIsSelected() {
        MapleMap map = observedMap();
        Character player = eligibleCharacter(1, map, mock(Client.class), false);
        when(map.getAllPlayers()).thenReturn(List.of(player));
        Monster monster = monster(map);

        assertTrue(MonsterSimulationControllerResolver.hasObserver(map));
        assertEquals(player, MonsterSimulationControllerResolver.resolve(monster));
    }

    @Test
    void hiddenGmAloneStillActivates() {
        MapleMap map = observedMap();
        Character hiddenGm = eligibleCharacter(2, map, mock(Client.class), true);
        when(map.getAllPlayers()).thenReturn(List.of(hiddenGm));

        assertTrue(MonsterSimulationControllerResolver.hasObserver(map));
        assertEquals(hiddenGm, MonsterSimulationControllerResolver.resolve(monster(map)));
    }

    @Test
    void headlessAgentAndTransitioningClientAreNeverControllers() {
        MapleMap map = observedMap();
        Character agent = eligibleCharacter(3, map, mock(BotClient.class), false);
        Character transitioning = eligibleCharacter(4, map, mock(Client.class), false);
        when(transitioning.isChangingMaps()).thenReturn(true);
        when(map.getAllPlayers()).thenReturn(List.of(agent, transitioning));
        AgentPresence.install(candidate -> candidate == agent);

        assertFalse(MonsterSimulationControllerResolver.hasObserver(map));
        assertNull(MonsterSimulationControllerResolver.resolve(monster(map)));
    }

    @Test
    void characterWithoutClientIsNotAnObserverOrController() {
        MapleMap map = observedMap();
        Character disconnected = eligibleCharacter(5, map, null, false);
        when(map.getAllPlayers()).thenReturn(List.of(disconnected));

        assertFalse(MonsterSimulationControllerResolver.hasObserver(map));
        assertNull(MonsterSimulationControllerResolver.resolve(monster(map)));
    }

    private static MapleMap observedMap() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        return map;
    }

    private static Monster monster(MapleMap map) {
        Monster monster = mock(Monster.class);
        when(monster.getMap()).thenReturn(map);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        return monster;
    }

    private static Character eligibleCharacter(int id, MapleMap map, Client client, boolean hidden) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMap()).thenReturn(map);
        when(character.getClient()).thenReturn(client);
        when(character.isLoggedinWorld()).thenReturn(true);
        when(character.isChangingMaps()).thenReturn(false);
        when(character.isHidden()).thenReturn(hidden);
        when(character.getPosition()).thenReturn(new Point(id, 0));
        return character;
    }
}
