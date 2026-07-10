package client;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonsterBookPersistenceDirtyTest {
    @Test
    void acceptedCardsMarkStatsAndCappedCardsDoNot() {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        when(client.getPlayer()).thenReturn(character);
        when(character.getMap()).thenReturn(mock(MapleMap.class));
        MonsterBook book = new MonsterBook();
        AtomicInteger marks = new AtomicInteger();
        book.setPersistenceDirtyMarker(marks::incrementAndGet);

        for (int i = 0; i < 6; i++) {
            book.addCard(client, 2380000);
        }

        assertEquals(5, marks.get());
        assertEquals(5, book.getCards().get(2380000));
    }
}
