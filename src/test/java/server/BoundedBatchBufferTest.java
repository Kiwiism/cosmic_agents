package server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BoundedBatchBufferTest {
    @Test
    void boundsBacklogAndDrainsInBatches() {
        BoundedBatchBuffer<Integer> buffer = new BoundedBatchBuffer<>(3);
        buffer.offer(1);
        buffer.offer(2);
        buffer.offer(3);

        assertFalse(buffer.offer(4));
        assertEquals(List.of(1, 2), buffer.drain(2));
        assertEquals(1, buffer.size());
    }

    @Test
    void reportsRecordsLostWhenFailureRequeueMeetsNewBacklog() {
        BoundedBatchBuffer<Integer> buffer = new BoundedBatchBuffer<>(2);
        buffer.offer(9);

        assertEquals(1, buffer.requeue(List.of(1, 2)));
        assertEquals(2, buffer.size());
    }
}
