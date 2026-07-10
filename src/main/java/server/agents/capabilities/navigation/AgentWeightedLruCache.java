package server.agents.capabilities.navigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

final class AgentWeightedLruCache<K, V> {
    private final long maximumWeight;
    private final ToLongFunction<V> weigher;
    private final LinkedHashMap<K, WeightedValue<V>> values = new LinkedHashMap<>(16, 0.75f, true);
    private long currentWeight;
    private long evictionCount;

    AgentWeightedLruCache(long maximumWeight, ToLongFunction<V> weigher) {
        if (maximumWeight <= 0) {
            throw new IllegalArgumentException("maximumWeight must be positive");
        }
        this.maximumWeight = maximumWeight;
        this.weigher = weigher;
    }

    synchronized V get(K key) {
        WeightedValue<V> value = values.get(key);
        return value == null ? null : value.value();
    }

    synchronized boolean containsKey(K key) {
        return values.containsKey(key);
    }

    synchronized List<K> put(K key, V value) {
        long weight = Math.max(1L, weigher.applyAsLong(value));
        WeightedValue<V> previous = values.put(key, new WeightedValue<>(value, weight));
        if (previous != null) {
            currentWeight -= previous.weight();
        }
        currentWeight += weight;

        List<K> evicted = new ArrayList<>();
        var iterator = values.entrySet().iterator();
        while (currentWeight > maximumWeight && iterator.hasNext()) {
            Map.Entry<K, WeightedValue<V>> eldest = iterator.next();
            currentWeight -= eldest.getValue().weight();
            evicted.add(eldest.getKey());
            iterator.remove();
            evictionCount++;
        }
        return List.copyOf(evicted);
    }

    synchronized List<Map.Entry<K, V>> snapshotEntries() {
        return values.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().value()))
                .toList();
    }

    synchronized boolean anyKeyMatches(Predicate<K> predicate) {
        return values.keySet().stream().anyMatch(predicate);
    }

    synchronized int size() {
        return values.size();
    }

    synchronized long currentWeight() {
        return currentWeight;
    }

    synchronized long evictionCount() {
        return evictionCount;
    }

    synchronized void clear() {
        values.clear();
        currentWeight = 0L;
    }

    private record WeightedValue<V>(V value, long weight) {
    }
}
