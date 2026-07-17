package server.maps;

import org.junit.jupiter.api.Test;
import server.life.Monster;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleMapPerceptionSnapshotTest {
    @Test
    void sharesSnapshotUntilObjectMembershipChanges() {
        MapleMap map = new MapleMap(1, 0, 1, 1, 1.0f);
        Monster monster = mapObject(Monster.class, MapObjectType.MONSTER);
        MapItem item = mapObject(MapItem.class, MapObjectType.ITEM);
        map.addMapObject(monster);
        map.addMapObject(item);

        MapPerceptionSnapshot first = map.getPerceptionSnapshot();
        assertSame(first, map.getPerceptionSnapshot());
        assertEquals(java.util.List.of(monster), first.monsters());
        assertEquals(java.util.List.of(item), first.items());
        assertThrows(UnsupportedOperationException.class, () -> first.monsters().clear());

        map.removeMapObject(monster.getObjectId());

        MapPerceptionSnapshot afterRemoval = map.getPerceptionSnapshot();
        assertNotSame(first, afterRemoval);
        assertEquals(first.revision() + 1, afterRemoval.revision());
        assertEquals(java.util.List.of(), afterRemoval.monsters());
        assertEquals(java.util.List.of(item), afterRemoval.items());
    }

    private static <T extends MapObject> T mapObject(Class<T> type, MapObjectType objectType) {
        T object = mock(type);
        AtomicInteger objectId = new AtomicInteger();
        doAnswer(invocation -> {
            objectId.set(invocation.getArgument(0));
            return null;
        }).when(object).setObjectId(anyInt());
        when(object.getObjectId()).thenAnswer(invocation -> objectId.get());
        when(object.getType()).thenReturn(objectType);
        return object;
    }
}
