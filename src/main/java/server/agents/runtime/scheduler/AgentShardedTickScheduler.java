package server.agents.runtime.scheduler;

import server.agents.monitoring.AgentSchedulerRegistrationSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Fixed stable-hash owner set for explicit central-sharded scheduling. */
public final class AgentShardedTickScheduler {
    public record ShutdownResult(int shards,
                                 int registrations,
                                 int cancelled,
                                 int remaining,
                                 boolean timedOut) {
    }

    private static final class Holder {
        private static final AgentShardedTickScheduler INSTANCE = createRuntime();
    }

    private final List<AgentTickScheduler> shards;

    AgentShardedTickScheduler(List<AgentTickScheduler> shards) {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("At least one Agent scheduler shard is required");
        }
        this.shards = List.copyOf(shards);
    }

    public static AgentShardedTickScheduler instance() {
        return Holder.INSTANCE;
    }

    public AgentScheduleHandle register(AgentRuntimeEntry entry, Runnable tick, long periodMs) {
        AgentSessionId sessionId = AgentSessionId.from(entry);
        return shards.get(shardIndex(sessionId, shards.size())).register(entry, tick, periodMs);
    }

    public int shardCount() {
        return shards.size();
    }

    public List<Integer> registrationCounts() {
        return shards.stream().map(AgentTickScheduler::registrationCount).toList();
    }

    public int registrationImbalance() {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (AgentTickScheduler shard : shards) {
            int count = shard.registrationCount();
            minimum = Math.min(minimum, count);
            maximum = Math.max(maximum, count);
        }
        return maximum - minimum;
    }

    public List<AgentSchedulerRegistrationSnapshot> registrationSnapshots() {
        return shards.stream()
                .flatMap(shard -> shard.registrationSnapshots().stream())
                .sorted(java.util.Comparator
                        .comparingInt((AgentSchedulerRegistrationSnapshot snapshot) ->
                                snapshot.sessionId().agentCharacterId())
                        .thenComparingLong(snapshot -> snapshot.sessionId().generation()))
                .toList();
    }

    public void start() {
        shards.forEach(AgentTickScheduler::start);
    }

    public ShutdownResult shutdownAndDrain(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent scheduler shutdown timeout must not be negative");
        }
        long deadline = System.nanoTime() + Math.max(0L, timeout.toNanos());
        int registrations = 0;
        int cancelled = 0;
        int remaining = 0;
        boolean timedOut = false;
        for (AgentTickScheduler shard : shards) {
            long remainingNanos = Math.max(0L, deadline - System.nanoTime());
            AgentTickScheduler.ShutdownResult result = shard.shutdownAndDrain(Duration.ofNanos(remainingNanos));
            registrations += result.registrations();
            cancelled += result.cancelled();
            remaining += result.remaining();
            timedOut |= result.timedOut();
        }
        return new ShutdownResult(shards.size(), registrations, cancelled, remaining, timedOut);
    }

    static int shardIndex(AgentSessionId sessionId, int shardCount) {
        if (sessionId == null || shardCount < 1) {
            throw new IllegalArgumentException("Agent session and positive shard count are required");
        }
        long mixed = ((long) sessionId.agentCharacterId() << 32) ^ sessionId.generation();
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return Math.floorMod(Long.hashCode(mixed), shardCount);
    }

    private static AgentShardedTickScheduler createRuntime() {
        AgentSchedulerConfig config = AgentSchedulerConfig.fromSystemProperties();
        List<AgentTickScheduler> shards = new ArrayList<>(config.shardCount());
        for (int shardId = 0; shardId < config.shardCount(); shardId++) {
            shards.add(new AgentTickScheduler(
                    System::currentTimeMillis,
                    System::nanoTime,
                    (task, periodMs) -> AgentSchedulerRuntime.register(task, periodMs),
                    (task, delayMs) -> AgentSchedulerRuntime.schedule(task, delayMs),
                    config,
                    shardId));
        }
        return new AgentShardedTickScheduler(shards);
    }
}
