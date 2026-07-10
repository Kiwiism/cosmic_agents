package client;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.SavedLocation;
import server.maps.SavedLocationType;
import server.quest.Quest;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterDomainPersistenceRoundTripTest {
    @Test
    void questStatusAndProgressRoundTripThroughProductionSnapshot() {
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 1000);
        QuestStatus original = new QuestStatus(quest, QuestStatus.Status.NOT_STARTED);
        original.setStatus(QuestStatus.Status.STARTED);
        original.setCompletionTime(123_000L);
        original.setExpirationTime(456_000L);
        original.setForfeited(2);
        original.setCompleted(3);
        original.setProgress(100100, "007");
        original.addMedalMap(100000000);

        QuestStatus restored = QuestStatus.fromPersistenceSnapshot(quest, original.persistenceSnapshot());

        assertEquals(original.getQuestID(), restored.getQuestID());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getCompletionTime(), restored.getCompletionTime());
        assertEquals(original.getExpirationTime(), restored.getExpirationTime());
        assertEquals(original.getForfeited(), restored.getForfeited());
        assertEquals(original.getCompleted(), restored.getCompleted());
        assertEquals(original.getProgress(), restored.getProgress());
        assertEquals(original.getMedalMaps(), restored.getMedalMaps());
    }

    @Test
    void monsterBookRoundTripsCardsAndDerivedCounts() {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        when(client.getPlayer()).thenReturn(character);
        when(character.getMap()).thenReturn(mock(MapleMap.class));
        MonsterBook original = new MonsterBook();
        original.addCard(client, 2380000);
        original.addCard(client, 2388000);

        MonsterBook restored = MonsterBook.fromPersistenceSnapshot(original.persistenceSnapshot());

        assertEquals(Map.of(2380000, 1, 2388000, 1), restored.getCards());
        assertEquals(1, restored.getNormalCard());
        assertEquals(1, restored.getSpecialCard());
        assertEquals(original.getBookLevel(), restored.getBookLevel());
    }

    @Test
    void persistedBuddyCapacityAndGroupRoundTripWithoutRuntimeChannel() {
        BuddyList original = new BuddyList(35);
        original.put(new BuddylistEntry("Visible", "Party", 1, 2, true));

        BuddyList restored = BuddyList.fromPersistenceSnapshot(original.persistenceSnapshot());

        assertEquals(35, restored.getCapacity());
        assertEquals("Party", restored.get(1).getGroup());
        assertTrue(restored.get(1).isVisible());
        assertEquals(-1, restored.get(1).getChannel());
    }

    @Test
    void skillAndSavedLocationValueStateRoundTrip() {
        Character.SkillEntry skill = new Character.SkillEntry((byte) 20, 30, 987654321L);
        Character.SkillEntry restoredSkill = skill.persistenceCopy();
        SavedLocation location = new SavedLocation(100000000, 7);
        SavedLocation restoredLocation = new SavedLocation(location.getMapId(), location.getPortal());

        assertEquals(skill.skillevel, restoredSkill.skillevel);
        assertEquals(skill.masterlevel, restoredSkill.masterlevel);
        assertEquals(skill.expiration, restoredSkill.expiration);
        assertEquals(location.getMapId(), restoredLocation.getMapId());
        assertEquals(location.getPortal(), restoredLocation.getPortal());
    }

    @Test
    void savedAndTeleportRockLocationsRoundTripThroughProductionSnapshot() {
        SavedLocation[] locations = new SavedLocation[SavedLocationType.values().length];
        locations[SavedLocationType.FREE_MARKET.ordinal()] = new SavedLocation(910000000, 2);

        Character.LocationPersistenceSnapshot snapshot = new Character.LocationPersistenceSnapshot(
                locations, List.of(100000000, 101000000), List.of(200000000));

        assertEquals(910000000, snapshot.savedLocation(SavedLocationType.FREE_MARKET).getMapId());
        assertEquals(2, snapshot.savedLocation(SavedLocationType.FREE_MARKET).getPortal());
        assertEquals(List.of(100000000, 101000000), snapshot.regularTrockMaps());
        assertEquals(List.of(200000000), snapshot.vipTrockMaps());
    }

    @Test
    void mountRoundTripsEveryCharacterRowValue() {
        Character owner = mock(Character.class);
        Mount original = new Mount(owner, 1902000, 1004);
        original.setExp(1234);
        original.setLevel(7);
        original.setTiredness(55);

        Mount restored = Mount.fromPersistenceSnapshot(owner, 1902000, 1004,
                original.persistenceSnapshot());

        assertEquals(1234, restored.getExp());
        assertEquals(7, restored.getLevel());
        assertEquals(55, restored.getTiredness());
    }
}
