package server.agents.runtime.scheduler;

import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentIndexedMinHeapTest {
    @Test
    void ordersByDueTimeThenStableSequence() {
        AgentIndexedMinHeap<Node> heap = heap();
        Node later = new Node(200L, 1L);
        Node second = new Node(100L, 2L);
        Node first = new Node(100L, 1L);

        heap.addOrUpdate(later);
        heap.addOrUpdate(second);
        heap.addOrUpdate(first);

        assertEquals(first, heap.poll());
        assertEquals(second, heap.poll());
        assertEquals(later, heap.poll());
        assertNull(heap.poll());
    }

    @Test
    void reindexesWhenDueTimeMovesEarlierOrLater() {
        AgentIndexedMinHeap<Node> heap = heap();
        Node first = new Node(100L, 1L);
        Node second = new Node(200L, 2L);
        heap.addOrUpdate(first);
        heap.addOrUpdate(second);

        second.dueMs = 50L;
        heap.addOrUpdate(second);
        assertEquals(second, heap.peek());

        second.dueMs = 300L;
        heap.addOrUpdate(second);
        assertEquals(first, heap.peek());
    }

    @Test
    void removalReleasesIndexAndPreservesHeap() {
        AgentIndexedMinHeap<Node> heap = heap();
        Node first = new Node(100L, 1L);
        Node second = new Node(200L, 2L);
        Node third = new Node(300L, 3L);
        heap.addOrUpdate(first);
        heap.addOrUpdate(second);
        heap.addOrUpdate(third);

        assertTrue(heap.remove(second));
        assertFalse(heap.remove(second));
        assertEquals(2, heap.size());
        assertEquals(first, heap.poll());
        assertEquals(third, heap.poll());
        assertTrue(heap.isEmpty());
    }

    private static AgentIndexedMinHeap<Node> heap() {
        return new AgentIndexedMinHeap<>(
                Comparator.comparingLong((Node node) -> node.dueMs)
                        .thenComparingLong(node -> node.sequence));
    }

    private static final class Node {
        private long dueMs;
        private final long sequence;

        private Node(long dueMs, long sequence) {
            this.dueMs = dueMs;
            this.sequence = sequence;
        }
    }
}
