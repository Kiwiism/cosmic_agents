package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentShardedTickSchedulerTest {
    @AfterEach
    void tearDown() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void stableHashAlwaysSelectsTheSameShardForOneSession() {
        AgentSessionId session = new AgentSessionId(1234, 99L);

        assertEquals(
                AgentShardedTickScheduler.shardIndex(session, 4),
                AgentShardedTickScheduler.shardIndex(session, 4));

        int[] counts = new int[4];
        for (int agentId = 1; agentId <= 2_000; agentId++) {
            counts[AgentShardedTickScheduler.shardIndex(new AgentSessionId(agentId, agentId), 4)]++;
        }
        int minimum = java.util.Arrays.stream(counts).min().orElseThrow();
        int maximum = java.util.Arrays.stream(counts).max().orElseThrow();
        assertTrue(maximum - minimum < 100);
    }

    @Test
    void registrationsAreOwnedByExactlyOneShardAndRetainMode() {
        List<AgentTickScheduler> shards = new ArrayList<>();
        for (int shardId = 0; shardId < 4; shardId++) {
            shards.add(shard(shardId));
        }
        AgentShardedTickScheduler scheduler = new AgentShardedTickScheduler(shards);
        AtomicInteger ticks = new AtomicInteger();

        for (int agentId = 100; agentId < 116; agentId++) {
            AgentRuntimeEntry entry = entry(agentId);
            AgentRuntimeRegistry.registerEntry(1, entry);
            AgentScheduleHandle handle = scheduler.register(entry, ticks::incrementAndGet, 50L);
            assertEquals(AgentSchedulerMode.CENTRAL_SHARDED, handle.mode());
        }

        assertEquals(16, scheduler.registrationCounts().stream().mapToInt(Integer::intValue).sum());
        shards.forEach(AgentTickScheduler::tickAll);
        assertEquals(16, ticks.get());
    }

    @Test
    void concurrentShardsIsolateOneFailingAgent() throws Exception {
        List<AgentTickScheduler> shards = shards(4);
        AgentShardedTickScheduler scheduler = new AgentShardedTickScheduler(shards);
        AtomicInteger successfulTicks = new AtomicInteger();
        int sessionCount = 64;

        for (int agentId = 1; agentId <= sessionCount; agentId++) {
            AgentRuntimeEntry entry = entry(agentId);
            AgentRuntimeRegistry.registerEntry(1, entry);
            Runnable tick = agentId == 17
                    ? () -> {
                        throw new IllegalStateException("expected test failure");
                    }
                    : successfulTicks::incrementAndGet;
            scheduler.register(entry, tick, 50L);
        }

        runShardsConcurrently(shards);

        assertEquals(sessionCount - 1, successfulTicks.get());
        assertEquals(sessionCount, scheduler.registrationCounts().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void concurrentCancellationRetiresEveryShardRegistration() throws Exception {
        List<AgentTickScheduler> shards = shards(4);
        AgentShardedTickScheduler scheduler = new AgentShardedTickScheduler(shards);
        List<AgentScheduleHandle> handles = new ArrayList<>();

        for (int agentId = 1; agentId <= 128; agentId++) {
            AgentRuntimeEntry entry = entry(agentId);
            AgentRuntimeRegistry.registerEntry(1, entry);
            handles.add(scheduler.register(entry, () -> {
            }, 50L));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            executor.invokeAll(handles.stream()
                    .<Callable<Boolean>>map(handle -> () -> handle.cancel(false))
                    .toList());
        }
        runShardsConcurrently(shards);

        assertEquals(0, scheduler.registrationCounts().stream().mapToInt(Integer::intValue).sum());
        assertTrue(handles.stream().allMatch(AgentScheduleHandle::isCancelled));
    }

    @Test
    void sameAndDifferentMapAgentsExecuteOnceOnTheirSessionShard() throws Exception {
        List<AgentTickScheduler> shards = shards(4);
        AgentShardedTickScheduler scheduler = new AgentShardedTickScheduler(shards);
        ConcurrentHashMap<Integer, AtomicInteger> updatesByAgent = new ConcurrentHashMap<>();

        for (int agentId = 1; agentId <= 96; agentId++) {
            int mapId = agentId <= 48 ? 100000000 : 100000000 + agentId;
            AgentRuntimeEntry entry = entry(agentId, mapId);
            AgentRuntimeRegistry.registerEntry(1, entry);
            updatesByAgent.put(agentId, new AtomicInteger());
            scheduler.register(entry, updatesByAgent.get(agentId)::incrementAndGet, 50L);
        }

        runShardsConcurrently(shards);

        assertEquals(96, updatesByAgent.size());
        assertTrue(updatesByAgent.values().stream().allMatch(count -> count.get() == 1));
    }

    private static List<AgentTickScheduler> shards(int count) {
        List<AgentTickScheduler> shards = new ArrayList<>();
        for (int shardId = 0; shardId < count; shardId++) {
            shards.add(shard(shardId));
        }
        return shards;
    }

    private static void runShardsConcurrently(List<AgentTickScheduler> shards) throws Exception {
        try (var executor = Executors.newFixedThreadPool(shards.size())) {
            executor.invokeAll(shards.stream()
                    .<Callable<Void>>map(shard -> () -> {
                        shard.tickAll();
                        return null;
                    })
                    .toList());
        }
    }

    private static AgentTickScheduler shard(int shardId) {
        AgentSchedulerConfig config = new AgentSchedulerConfig(
                AgentSchedulerMode.CENTRAL_SHARDED,
                50L,
                false,
                250L,
                0,
                64,
                1_000L,
                64,
                40,
                10,
                2_000L,
                4);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        return new AgentTickScheduler(
                () -> 1_000L,
                System::nanoTime,
                (task, period) -> future,
                (task, delay) -> future,
                config,
                shardId);
    }

    private static AgentRuntimeEntry entry(int agentId) {
        return entry(agentId, 100000000);
    }

    private static AgentRuntimeEntry entry(int agentId, int mapId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMapId()).thenReturn(mapId);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
