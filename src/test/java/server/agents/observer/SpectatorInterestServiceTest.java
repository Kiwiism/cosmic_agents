package server.agents.observer;

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

class SpectatorInterestServiceTest {
    private boolean observerPreviouslyEnabled;

    @BeforeEach
    void reset() {
        observerPreviouslyEnabled = YamlConfig.config.server.observer.enabled;
        YamlConfig.config.server.observer.enabled = true;
        SpectatorInterestService.resetForTests();
    }

    @AfterEach
    void restoreObserverSetting() {
        YamlConfig.config.server.observer.enabled = observerPreviouslyEnabled;
    }

    @Test
    void isolatesWorldsAndReturnsOnlyNewerEvents() {
        Character first = character(11, 0, 100000000, "First");
        Character second = character(22, 1, 103000000, "Second");

        SpectatorInterestService.publish(
                first,
                SpectatorInterestService.Type.LEVEL_UP,
                90,
                "Reached level 15");
        long firstSequence = SpectatorInterestService.latestSequence(0);
        SpectatorInterestService.publish(
                first,
                SpectatorInterestService.Type.QUEST_COMPLETE,
                75,
                "Completed quest 1001");
        SpectatorInterestService.publish(
                second,
                SpectatorInterestService.Type.BOSS_DEFEAT,
                100,
                "Defeated Test Boss");

        List<SpectatorInterestService.Event> worldZero =
                SpectatorInterestService.eventsSince(0, firstSequence);
        assertEquals(1, worldZero.size());
        assertEquals(11, worldZero.getFirst().characterId());
        assertEquals(SpectatorInterestService.Type.QUEST_COMPLETE,
                worldZero.getFirst().type());

        List<SpectatorInterestService.Event> worldOne =
                SpectatorInterestService.eventsSince(1, 0);
        assertEquals(1, worldOne.size());
        assertEquals(22, worldOne.getFirst().characterId());
    }

    @Test
    void boundsScoresAndText() {
        Character character = character(
                33, 0, 104000000, "ACharacterNameThatIsFarTooLongForTheFeed");
        SpectatorInterestService.publish(
                character,
                SpectatorInterestService.Type.JOB_ADVANCE,
                50_000,
                "x".repeat(300));

        SpectatorInterestService.Event event =
                SpectatorInterestService.eventsSince(0, 0).getFirst();
        assertEquals(1_000, event.score());
        assertEquals(32, event.characterName().length());
        assertEquals(160, event.detail().length());
        assertTrue(event.sequence() > 0);
    }

    @Test
    void disabledObserverDoesNotCollectEvents() {
        YamlConfig.config.server.observer.enabled = false;

        SpectatorInterestService.publish(
                character(44, 0, 100000000, "Disabled"),
                SpectatorInterestService.Type.LEVEL_UP,
                90,
                "This event should not be retained");

        assertTrue(SpectatorInterestService.eventsSince(0, 0).isEmpty());
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
