package server.agents.runtime.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Indexed minimum heap for shard-owned due-time records. */
final class AgentIndexedMinHeap<T> {
    private final List<T> values = new ArrayList<>();
    private final Map<T, Integer> indices = new IdentityHashMap<>();
    private final Comparator<? super T> comparator;

    AgentIndexedMinHeap(Comparator<? super T> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Heap comparator is required");
        }
        this.comparator = comparator;
    }

    void addOrUpdate(T value) {
        Integer index = indices.get(value);
        if (index == null) {
            int addedIndex = values.size();
            values.add(value);
            indices.put(value, addedIndex);
            siftUp(addedIndex);
            return;
        }
        if (!siftUp(index)) {
            siftDown(index);
        }
    }

    boolean remove(T value) {
        Integer index = indices.get(value);
        if (index == null) {
            return false;
        }
        removeAt(index);
        return true;
    }

    T peek() {
        return values.isEmpty() ? null : values.getFirst();
    }

    T poll() {
        return values.isEmpty() ? null : removeAt(0);
    }

    int size() {
        return values.size();
    }

    void clear() {
        values.clear();
        indices.clear();
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    private T removeAt(int index) {
        int lastIndex = values.size() - 1;
        T removed = values.get(index);
        indices.remove(removed);
        if (index == lastIndex) {
            values.remove(lastIndex);
            return removed;
        }

        T moved = values.remove(lastIndex);
        values.set(index, moved);
        indices.put(moved, index);
        if (!siftUp(index)) {
            siftDown(index);
        }
        return removed;
    }

    private boolean siftUp(int initialIndex) {
        int index = initialIndex;
        while (index > 0) {
            int parentIndex = (index - 1) >>> 1;
            if (comparator.compare(values.get(index), values.get(parentIndex)) >= 0) {
                break;
            }
            swap(index, parentIndex);
            index = parentIndex;
        }
        return index != initialIndex;
    }

    private void siftDown(int initialIndex) {
        int index = initialIndex;
        int size = values.size();
        while (true) {
            int leftIndex = (index << 1) + 1;
            if (leftIndex >= size) {
                return;
            }
            int rightIndex = leftIndex + 1;
            int smallerChild = rightIndex < size
                    && comparator.compare(values.get(rightIndex), values.get(leftIndex)) < 0
                    ? rightIndex
                    : leftIndex;
            if (comparator.compare(values.get(smallerChild), values.get(index)) >= 0) {
                return;
            }
            swap(index, smallerChild);
            index = smallerChild;
        }
    }

    private void swap(int firstIndex, int secondIndex) {
        T first = values.get(firstIndex);
        T second = values.get(secondIndex);
        values.set(firstIndex, second);
        values.set(secondIndex, first);
        indices.put(first, secondIndex);
        indices.put(second, firstIndex);
    }
}
