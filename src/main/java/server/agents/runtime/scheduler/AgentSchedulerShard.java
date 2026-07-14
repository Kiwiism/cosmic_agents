package server.agents.runtime.scheduler;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** One scheduler-owned due heap with bounded multi-producer ingress. */
final class AgentSchedulerShard<T> {
    private final ArrayBlockingQueue<T> ingress;
    private final AgentIndexedMinHeap<T> dueHeap;
    private final Map<AgentPriorityClass, ArrayDeque<T>> readyQueues =
            new EnumMap<>(AgentPriorityClass.class);
    private final Map<T, AgentPriorityClass> readyMembership = new IdentityHashMap<>();
    private final AtomicInteger ingressHighWaterMark = new AtomicInteger();

    AgentSchedulerShard(int ingressCapacity, Comparator<? super T> dueComparator) {
        if (ingressCapacity < 1) {
            throw new IllegalArgumentException("Scheduler ingress capacity must be positive");
        }
        ingress = new ArrayBlockingQueue<>(ingressCapacity);
        dueHeap = new AgentIndexedMinHeap<>(dueComparator);
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            readyQueues.put(priority, new ArrayDeque<>());
        }
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
        if (!readyMembership.containsKey(value)) {
            dueHeap.addOrUpdate(value);
        }
    }

    boolean remove(T value) {
        boolean removed = dueHeap.remove(value);
        AgentPriorityClass priority = readyMembership.remove(value);
        if (priority != null) {
            removeIdentity(readyQueues.get(priority), value);
            removed = true;
        }
        return removed;
    }

    T peekDue() {
        return dueHeap.peek();
    }

    T pollDue() {
        return dueHeap.poll();
    }

    void addReady(T value, AgentPriorityClass priority) {
        if (readyMembership.putIfAbsent(value, priority) == null) {
            readyQueues.get(priority).addLast(value);
        }
    }

    void addReadyFirst(T value, AgentPriorityClass priority) {
        if (readyMembership.putIfAbsent(value, priority) == null) {
            readyQueues.get(priority).addFirst(value);
        }
    }

    boolean containsReady(T value) {
        return readyMembership.containsKey(value);
    }

    void updateReadyPriority(T value, AgentPriorityClass priority) {
        AgentPriorityClass previous = readyMembership.get(value);
        if (previous == null || previous == priority) {
            return;
        }
        removeIdentity(readyQueues.get(previous), value);
        readyMembership.put(value, priority);
        readyQueues.get(priority).addLast(value);
    }

    boolean hasReady(int maximumEffectivePriority, ToIntFunction<T> effectivePriority) {
        return selectReady(maximumEffectivePriority, effectivePriority, null) != null;
    }

    T pollReady(int maximumEffectivePriority,
                ToIntFunction<T> effectivePriority,
                Comparator<? super T> readyComparator) {
        T selected = selectReady(maximumEffectivePriority, effectivePriority, readyComparator);
        if (selected == null) {
            return null;
        }
        AgentPriorityClass priority = readyMembership.remove(selected);
        readyQueues.get(priority).removeFirst();
        return selected;
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

    int readyCount() {
        return readyMembership.size();
    }

    boolean isIdle() {
        return ingress.isEmpty() && dueHeap.isEmpty() && readyMembership.isEmpty();
    }

    void clear() {
        ingress.clear();
        dueHeap.clear();
        readyQueues.values().forEach(ArrayDeque::clear);
        readyMembership.clear();
    }

    private T selectReady(int maximumEffectivePriority,
                          ToIntFunction<T> effectivePriority,
                          Comparator<? super T> readyComparator) {
        T selected = null;
        int selectedEffectivePriority = Integer.MAX_VALUE;
        for (AgentPriorityClass basePriority : AgentPriorityClass.values()) {
            T candidate = readyQueues.get(basePriority).peekFirst();
            if (candidate == null) {
                continue;
            }
            int candidateEffectivePriority = effectivePriority.applyAsInt(candidate);
            if (candidateEffectivePriority > maximumEffectivePriority) {
                continue;
            }
            if (candidateEffectivePriority < selectedEffectivePriority
                    || candidateEffectivePriority == selectedEffectivePriority
                    && readyComparator != null
                    && readyComparator.compare(candidate, selected) < 0) {
                selected = candidate;
                selectedEffectivePriority = candidateEffectivePriority;
            }
        }
        return selected;
    }

    private static <T> void removeIdentity(ArrayDeque<T> queue, T value) {
        queue.removeIf(candidate -> candidate == value);
    }
}
