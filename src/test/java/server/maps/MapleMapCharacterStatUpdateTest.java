package server.maps;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapleMapCharacterStatUpdateTest {
    @Test
    void coalescesPendingUpdatesForTheSameCharacter() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        AtomicInteger first = new AtomicInteger();
        AtomicInteger latest = new AtomicInteger();

        map.registerCharacterStatUpdate(10, first::incrementAndGet);
        map.registerCharacterStatUpdate(10, latest::incrementAndGet);

        assertEquals(1, map.pendingCharacterStatUpdateCount());
        map.runCharacterStatUpdate();
        assertEquals(0, first.get());
        assertEquals(1, latest.get());
        assertEquals(0, map.pendingCharacterStatUpdateCount());
    }

    @Test
    void runsUpdatesForDifferentCharactersIndependently() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        AtomicInteger updates = new AtomicInteger();

        map.registerCharacterStatUpdate(10, updates::incrementAndGet);
        map.registerCharacterStatUpdate(20, updates::incrementAndGet);
        map.runCharacterStatUpdate();

        assertEquals(2, updates.get());
    }
}
