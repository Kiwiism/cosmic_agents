package server.maps;

import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MapleMapLifecycleTest {
    @Test
    void emptyPlainMapCanBecomeDormantAndUnloadable() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);

        assertTrue(map.shouldSkipDormantUpdate(0));
        assertTrue(map.isSafeToUnload(0));
    }

    @Test
    void scriptedAndEventMapsAreNeverUnloadCandidates() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        map.setOnUserEnter("scriptedEntry");
        assertFalse(map.isSafeToUnload(0));

        map.setOnUserEnter("");
        map.setEventInstance(mock(EventInstanceManager.class));
        assertFalse(map.isSafeToUnload(0));
    }

    @Test
    void environmentGetterReturnsAnImmutableSnapshot() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        map.moveEnvironment("gate", 1);
        var snapshot = map.getEnvironment();
        map.moveEnvironment("gate", 2);

        assertEquals(1, snapshot.get("gate"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", 1));
    }

    @Test
    void unloadedMapIsFreshlyLoadedOnNextAccess() {
        AtomicInteger loads = new AtomicInteger();
        MapManager manager = new MapManager(null, 0, 1, (mapId, world, channel, event) -> {
            MapleMap loaded = new MapleMap(mapId, world, channel, 1, 1.0f);
            loaded.moveEnvironment("load-generation", loads.incrementAndGet());
            return loaded;
        });

        MapleMap first = manager.getMap(100000000);
        assertTrue(manager.unloadIfStillIdle(first, 0));
        assertFalse(manager.isMapLoaded(100000000));

        MapleMap reloaded = manager.getMap(100000000);
        assertNotSame(first, reloaded);
        assertEquals(2, loads.get());
        assertEquals(2, reloaded.getEnvironment().get("load-generation"));
        assertEquals(1, manager.unloadedMapCount());
    }

    @Test
    void pendingCharacterWorkPreventsUnload() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        map.registerCharacterStatUpdate(10, () -> { });

        assertFalse(map.isSafeToUnload(0));
    }

    @Test
    void staleMapReferenceCannotEvictItsReplacement() {
        AtomicInteger loads = new AtomicInteger();
        MapManager manager = new MapManager(null, 0, 1, (mapId, world, channel, event) -> {
            loads.incrementAndGet();
            return new MapleMap(mapId, world, channel, 1, 1.0f);
        });
        MapleMap first = manager.getMap(100000000);
        MapleMap replacement = manager.resetMap(100000000);

        assertFalse(manager.unloadIfStillIdle(first, 0));
        assertEquals(replacement, manager.getLoadedMap(100000000));
        assertEquals(2, loads.get());
    }

    @Test
    void repeatedUnloadAndReloadDoesNotRetainStaleMaps() {
        AtomicInteger loads = new AtomicInteger();
        MapManager manager = new MapManager(null, 0, 1, (mapId, world, channel, event) -> {
            loads.incrementAndGet();
            return new MapleMap(mapId, world, channel, 1, 1.0f);
        });

        for (int cycle = 0; cycle < 1_000; cycle++) {
            int mapId = 100000000 + cycle % 25;
            MapleMap loaded = manager.getMap(mapId);
            assertTrue(manager.unloadIfStillIdle(loaded, 0));
            assertFalse(manager.isMapLoaded(mapId));
        }

        assertEquals(1_000, loads.get());
        assertEquals(1_000, manager.unloadedMapCount());
        assertEquals(0, manager.loadedMapCount());
    }
}
