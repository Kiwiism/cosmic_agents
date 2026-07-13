package server.agents.runtime.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSchedulerShardTest {
    @Test
    void ingressIsBoundedAndTracksHighWaterMark() {
        AgentSchedulerShard<Integer> shard = new AgentSchedulerShard<>(2, Comparator.naturalOrder());

        assertTrue(shard.offer(2));
        assertTrue(shard.offer(1));
        assertFalse(shard.offer(3));
        assertEquals(2, shard.ingressDepth());
        assertEquals(2, shard.ingressHighWaterMark());

        List<Integer> drained = new ArrayList<>();
        assertEquals(2, shard.drainIngress(drained::add));
        assertEquals(List.of(2, 1), drained);
        assertEquals(0, shard.ingressDepth());
    }

    @Test
    void dueHeapIsShardLocalAndIndexed() {
        AgentSchedulerShard<Integer> shard = new AgentSchedulerShard<>(2, Comparator.naturalOrder());

        shard.addOrUpdate(2);
        shard.addOrUpdate(1);

        assertEquals(2, shard.scheduledCount());
        assertEquals(1, shard.pollDue());
        assertTrue(shard.remove(2));
        assertTrue(shard.isIdle());
    }
}
