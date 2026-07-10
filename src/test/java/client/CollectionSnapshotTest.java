package client;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectionSnapshotTest {
    @Test
    void buddyListReturnsAnImmutablePointInTimeSnapshot() {
        BuddyList buddies = new BuddyList(10);
        buddies.put(new BuddylistEntry("A", "Default", 1, -1, true));
        Collection<BuddylistEntry> snapshot = buddies.getBuddies();

        buddies.put(new BuddylistEntry("B", "Default", 2, -1, true));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void monsterBookReturnsAnImmutablePointInTimeSnapshot() {
        MonsterBook monsterBook = new MonsterBook();
        Map<Integer, Integer> snapshot = monsterBook.getCards();
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(client.getPlayer()).thenReturn(character);
        when(character.getMap()).thenReturn(map);

        monsterBook.addCard(client, 2380000);

        assertEquals(Map.of(), snapshot);
        assertEquals(1, monsterBook.getCards().get(2380000));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(2380001, 1));
    }
}
