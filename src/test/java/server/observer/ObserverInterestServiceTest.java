package server.observer;

import client.Character;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObserverInterestServiceTest {
    private boolean observerPreviouslyEnabled;

    @BeforeEach
    void reset() {
        observerPreviouslyEnabled = YamlConfig.config.server.observer.enabled;
        YamlConfig.config.server.observer.enabled = true;
        ObserverInterestService.resetForTests();
    }

    @AfterEach
    void restoreObserverSetting() {
        YamlConfig.config.server.observer.enabled = observerPreviouslyEnabled;
    }

    @Test
    void isolatesWorldsAndReturnsOnlyNewerEvents() {
        Character first = character(11, 0, 100000000, "First");
        Character second = character(22, 1, 103000000, "Second");

        ObserverInterestService.publish(
                first,
                ObserverInterestService.Type.LEVEL_UP,
                90,
                "Reached level 15");
        long firstSequence = ObserverInterestService.latestSequence(0);
        ObserverInterestService.publish(
                first,
                ObserverInterestService.Type.QUEST_COMPLETE,
                75,
                "Completed quest 1001");
        ObserverInterestService.publish(
                second,
                ObserverInterestService.Type.BOSS_DEFEAT,
                100,
                "Defeated Test Boss");

        List<ObserverInterestService.Event> worldZero =
                ObserverInterestService.eventsSince(0, firstSequence);
        assertEquals(1, worldZero.size());
        assertEquals(11, worldZero.getFirst().characterId());
        assertEquals(ObserverInterestService.Type.QUEST_COMPLETE,
                worldZero.getFirst().type());

        List<ObserverInterestService.Event> worldOne =
                ObserverInterestService.eventsSince(1, 0);
        assertEquals(1, worldOne.size());
        assertEquals(22, worldOne.getFirst().characterId());
    }

    @Test
    void boundsScoresAndText() {
        Character character = character(
                33, 0, 104000000, "ACharacterNameThatIsFarTooLongForTheFeed");
        ObserverInterestService.publish(
                character,
                ObserverInterestService.Type.JOB_ADVANCE,
                50_000,
                "x".repeat(300));

        ObserverInterestService.Event event =
                ObserverInterestService.eventsSince(0, 0).getFirst();
        assertEquals(1_000, event.score());
        assertEquals(32, event.characterName().length());
        assertEquals(160, event.detail().length());
        assertTrue(event.sequence() > 0);
    }

    @Test
    void disabledObserverDoesNotCollectEvents() {
        YamlConfig.config.server.observer.enabled = false;

        ObserverInterestService.publish(
                character(44, 0, 100000000, "Disabled"),
                ObserverInterestService.Type.LEVEL_UP,
                90,
                "This event should not be retained");

        assertTrue(ObserverInterestService.eventsSince(0, 0).isEmpty());
    }

    private static Character character(
            int id,
            int world,
            int mapId,
            String name) {
        Character character = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(character.getMap()).thenReturn(map);
        when(character.getId()).thenReturn(id);
        when(character.getWorld()).thenReturn(world);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
