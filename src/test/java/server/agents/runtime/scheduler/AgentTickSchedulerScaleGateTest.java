package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickSchedulerScaleGateTest {
    private static final int AGENT_COUNT = 2_000;
    private static final int CADENCE_COUNT = 20;
    private static final int SHARD_COUNT = 4;

    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void fourShardsRunTwoThousandSessionsWithoutDuplicatesAndReleaseAllSchedulerState() throws Exception {
        AtomicLong now = new AtomicLong(1_000L);
        List<AgentTickScheduler> shards = shards(now);
        AgentShardedTickScheduler scheduler = new AgentShardedTickScheduler(shards);
        List<AgentScheduleHandle> handles = new ArrayList<>(AGENT_COUNT);
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger duplicateExecutions = new AtomicInteger();
        AtomicIntegerArray inFlight = new AtomicIntegerArray(AGENT_COUNT + 1);

        for (int agentId = 1; agentId <= AGENT_COUNT; agentId++) {
            int sessionAgentId = agentId;
            AgentRuntimeEntry entry = entry(agentId);
            AgentRuntimeRegistry.registerEntry(agentId, entry);
            handles.add(scheduler.register(entry, () -> {
                if (inFlight.getAndIncrement(sessionAgentId) != 0) {
                    duplicateExecutions.incrementAndGet();
                }
                try {
                    updates.incrementAndGet();
                } finally {
                    inFlight.decrementAndGet(sessionAgentId);
                }
            }, 50L));
        }

        assertEquals(AGENT_COUNT, scheduler.registrationCounts().stream().mapToInt(Integer::intValue).sum());
        assertTrue(scheduler.registrationImbalance() < 100);

        try (var executor = Executors.newFixedThreadPool(SHARD_COUNT)) {
            for (int cadence = 0; cadence < CADENCE_COUNT; cadence++) {
                runShards(executor, shards);
                now.addAndGet(50L);
            }
        }

        assertEquals(AGENT_COUNT * CADENCE_COUNT, updates.get());
        assertEquals(0, duplicateExecutions.get());

        handles.forEach(handle -> assertTrue(handle.cancel(false)));
        shards.forEach(AgentTickScheduler::tickAll);

        assertEquals(0, scheduler.registrationCounts().stream().mapToInt(Integer::intValue).sum());
        assertTrue(handles.stream().allMatch(AgentScheduleHandle::isCancelled));
        assertTrue(shards.stream().allMatch(shard -> shard.ownedRegistrationCount() == 0));
        assertTrue(shards.stream().allMatch(shard -> shard.scheduledRegistrationCount() == 0));
        assertTrue(shards.stream().allMatch(shard -> shard.readyRegistrationCount() == 0));
        assertTrue(shards.stream().allMatch(shard -> shard.ingressDepth() == 0));
    }

    private static void runShards(java.util.concurrent.ExecutorService executor,
                                  List<AgentTickScheduler> shards) throws InterruptedException {
        executor.invokeAll(shards.stream()
                .<Callable<Void>>map(shard -> () -> {
                    shard.tickAll();
                    return null;
                })
                .toList());
    }

    private static List<AgentTickScheduler> shards(AtomicLong now) {
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SHARDED,
                50L,
                false,
                250L,
                0,
                1_024,
                60_000L,
                4_096,
                40,
                10,
                2_000L,
                SHARD_COUNT);
        List<AgentTickScheduler> shards = new ArrayList<>(SHARD_COUNT);
        for (int shardId = 0; shardId < SHARD_COUNT; shardId++) {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            shards.add(new AgentTickScheduler(
                    now::get,
                    System::nanoTime,
                    (task, period) -> future,
                    (task, delay) -> future,
                    config,
                    shardId));
        }
        return shards;
    }

    private static AgentRuntimeEntry entry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
