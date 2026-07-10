package server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

final class BoundedBatchBuffer<T> {
    private final ArrayBlockingQueue<T> queue;

    BoundedBatchBuffer(int capacity) {
        queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    }

    boolean offer(T value) {
        return queue.offer(value);
    }

    List<T> drain(int maximum) {
        List<T> batch = new ArrayList<>(Math.min(Math.max(1, maximum), queue.size()));
        queue.drainTo(batch, Math.max(1, maximum));
        return batch;
    }

    int requeue(List<T> values) {
        int lost = 0;
        for (T value : values) {
            if (!queue.offer(value)) {
                lost++;
            }
        }
        return lost;
    }

    int size() {
        return queue.size();
    }

    int capacity() {
        return queue.size() + queue.remainingCapacity();
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }
}
