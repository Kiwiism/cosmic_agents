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
}
