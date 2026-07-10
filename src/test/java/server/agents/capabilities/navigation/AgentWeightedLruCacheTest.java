package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentWeightedLruCacheTest {
    @Test
    void evictsLeastRecentlyUsedValuesByWeight() {
        AgentWeightedLruCache<String, Integer> cache = new AgentWeightedLruCache<>(5, Integer::longValue);

        cache.put("first", 2);
        cache.put("second", 2);
        cache.get("first");

        assertEquals(List.of("second"), cache.put("third", 2));
        assertEquals(2, cache.get("first"));
        assertEquals(2, cache.get("third"));
        assertNull(cache.get("second"));
        assertEquals(4, cache.currentWeight());
        assertEquals(1, cache.evictionCount());
    }

    @Test
    void replacingValueUpdatesWeightWithoutGrowingEntryCount() {
        AgentWeightedLruCache<String, Integer> cache = new AgentWeightedLruCache<>(10, Integer::longValue);

        cache.put("graph", 3);
        cache.put("graph", 7);

        assertEquals(1, cache.size());
        assertEquals(7, cache.currentWeight());
        assertFalse(cache.snapshotEntries().isEmpty());
    }

    @Test
    void oversizedSingleValueIsEvictedRatherThanBreakingBound() {
        AgentWeightedLruCache<String, Integer> cache = new AgentWeightedLruCache<>(5, Integer::longValue);

        assertEquals(List.of("huge"), cache.put("huge", 6));

        assertEquals(0, cache.size());
        assertEquals(0, cache.currentWeight());
    }
}
