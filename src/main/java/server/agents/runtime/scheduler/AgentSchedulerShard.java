package server.agents.runtime.scheduler;

import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** One scheduler-owned due heap with bounded multi-producer ingress. */
final class AgentSchedulerShard<T> {
    private final ArrayBlockingQueue<T> ingress;
    private final AgentIndexedMinHeap<T> dueHeap;
    private final AtomicInteger ingressHighWaterMark = new AtomicInteger();

    AgentSchedulerShard(int ingressCapacity, Comparator<? super T> dueComparator) {
        if (ingressCapacity < 1) {
            throw new IllegalArgumentException("Scheduler ingress capacity must be positive");
        }
        ingress = new ArrayBlockingQueue<>(ingressCapacity);
        dueHeap = new AgentIndexedMinHeap<>(dueComparator);
    }

    boolean offer(T value) {
        boolean accepted = ingress.offer(value);
        if (accepted) {
            ingressHighWaterMark.accumulateAndGet(ingress.size(), Math::max);
        }
        return accepted;
    }

    int drainIngress(Consumer<? super T> consumer) {
        int drained = 0;
        T value;
        while ((value = ingress.poll()) != null) {
            consumer.accept(value);
            drained++;
        }
        return drained;
    }

    void addOrUpdate(T value) {
        dueHeap.addOrUpdate(value);
    }

    boolean remove(T value) {
        return dueHeap.remove(value);
    }

    T peekDue() {
        return dueHeap.peek();
    }

    T pollDue() {
        return dueHeap.poll();
    }

    int ingressDepth() {
        return ingress.size();
    }

    int ingressHighWaterMark() {
        return ingressHighWaterMark.get();
    }

    int scheduledCount() {
        return dueHeap.size();
    }

    boolean isIdle() {
        return ingress.isEmpty() && dueHeap.isEmpty();
    }
}
