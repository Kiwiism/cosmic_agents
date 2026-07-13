package server.agents.runtime.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/** One bounded central dispatcher for all Agent ticks when explicitly enabled. */
public final class AgentTickScheduler {
    private static final Logger log = LoggerFactory.getLogger(AgentTickScheduler.class);
    private static final AgentTickScheduler INSTANCE = new AgentTickScheduler(
            System::currentTimeMillis,
            (task, periodMs) -> AgentSchedulerRuntime.register(task, periodMs),
            (task, delayMs) -> AgentSchedulerRuntime.schedule(task, delayMs));

    private final Map<AgentRuntimeEntry, Registration> registrations = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong();
    private final AtomicLong roundRobinCursor = new AtomicLong();
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicBoolean wakeQueued = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final LongSupplier nowMs;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler;
    private final AgentSchedulerConfig config;
    private volatile ScheduledFuture<?> centralTask;

    public static AgentTickScheduler instance() {
        return INSTANCE;
    }

    public AgentTickScheduler(LongSupplier nowMs, BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler) {
        this(
                nowMs,
                loopScheduler,
                (task, delayMs) -> AgentSchedulerRuntime.schedule(task, delayMs),
                AgentSchedulerConfig.fromSystemProperties());
    }

    public AgentTickScheduler(LongSupplier nowMs,
                              BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                              BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler) {
        this(nowMs, loopScheduler, wakeScheduler, AgentSchedulerConfig.fromSystemProperties());
    }

    AgentTickScheduler(LongSupplier nowMs,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       AgentSchedulerConfig config) {
        this(
                nowMs,
                loopScheduler,
                (task, delayMs) -> AgentSchedulerRuntime.schedule(task, delayMs),
                config);
    }

    AgentTickScheduler(LongSupplier nowMs,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler,
                       AgentSchedulerConfig config) {
        this.nowMs = nowMs;
        this.loopScheduler = loopScheduler;
        this.wakeScheduler = wakeScheduler;
        this.config = config;
    }

    public AgentScheduleHandle register(AgentRuntimeEntry entry, Runnable tick, long periodMs) {
        if (entry == null || tick == null) {
            throw new IllegalArgumentException("Agent entry and tick are required");
        }
        Registration registration = new Registration(
                this,
                AgentSessionId.from(entry),
                entry,
                tick,
                Math.max(1L, periodMs),
                nowMs.getAsLong(),
                nextSequence.incrementAndGet());
        Registration previous = registrations.put(entry, registration);
        if (previous != null) {
            previous.cancel(false);
        }
        try {
            ensureCentralTask();
        } catch (RuntimeException | Error failure) {
            registrations.remove(entry, registration);
            throw failure;
        }
        return registration;
    }

    public void tickAll() {
        if (!ticking.compareAndSet(false, true)) {
            AgentSchedulerMetrics.recordSkipped(registrations.size());
            return;
        }
        long cycleStarted = System.nanoTime();
        try {
            long now = nowMs.getAsLong();
            List<Registration> due = new ArrayList<>();
            for (Registration registration : registrations.values()) {
                if (registration.isDue(now)) {
                    due.add(registration);
                }
            }
            due.sort(Comparator.comparingLong(Registration::sequence));

            int limit = config.maxAgentsPerTick();
            int updateCount = limit == 0 ? due.size() : Math.min(limit, due.size());
            if (updateCount < due.size()) {
                AgentSchedulerMetrics.recordSkipped(due.size() - updateCount);
            }
            int start = limit == 0 || due.isEmpty()
                    ? 0
                    : (int) Math.floorMod(roundRobinCursor.getAndAdd(updateCount), due.size());
            for (int i = 0; i < updateCount; i++) {
                update(due.get((start + i) % due.size()), now, config);
            }
        } finally {
            ticking.set(false);
            AgentSchedulerMetrics.recordCycle(System.nanoTime() - cycleStarted);
        }
    }

    public void pause(AgentRuntimeEntry entry) {
        Registration registration = registrations.get(entry);
        if (registration != null) {
            registration.paused.set(true);
        }
    }

    public void resume(AgentRuntimeEntry entry) {
        Registration registration = registrations.get(entry);
        if (registration != null) {
            registration.paused.set(false);
            registration.nextDueMs.set(nowMs.getAsLong());
        }
    }

    public int registrationCount() {
        return registrations.size();
    }

    private void update(Registration registration, long now, AgentSchedulerConfig config) {
        if (!registration.prepare(now)) {
            AgentSchedulerMetrics.recordSkipped(1);
            return;
        }
        long started = System.nanoTime();
        try {
            registration.tick.run();
        } catch (Throwable failure) {
            AgentSchedulerMetrics.recordFailure();
            log.warn("Central Agent scheduler tick failed for session {}", registration.sessionId, failure);
        } finally {
            long elapsedNs = System.nanoTime() - started;
            boolean slow = elapsedNs >= TimeUnit.MILLISECONDS.toNanos(config.slowTickMs());
            AgentSchedulerMetrics.recordUpdated(now - registration.claimedDueMs, slow);
            if (slow && config.logSlowTicks()) {
                log.warn("Slow central Agent tick session={} elapsedMs={}",
                        registration.sessionId, elapsedNs / 1_000_000L);
            }
        }
    }

    private void ensureCentralTask() {
        synchronized (lifecycleLock) {
            if (centralTask == null || centralTask.isCancelled() || centralTask.isDone()) {
                centralTask = loopScheduler.apply(
                        this::tickAll,
                        config.baseTickMs());
            }
        }
    }

    private void unregister(Registration registration) {
        registrations.remove(registration.entry, registration);
        synchronized (lifecycleLock) {
            if (registrations.isEmpty() && centralTask != null) {
                centralTask.cancel(false);
                centralTask = null;
            }
        }
    }

    private void queueWake() {
        if (!wakeQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            wakeScheduler.apply(() -> {
                wakeQueued.set(false);
                tickAll();
            }, 0L);
        } catch (RuntimeException | Error failure) {
            wakeQueued.set(false);
            throw failure;
        }
    }

    private static final class Registration implements AgentScheduleHandle {
        private final AgentTickScheduler owner;
        private final AgentSessionId sessionId;
        private final AgentRuntimeEntry entry;
        private final Runnable tick;
        private final long periodMs;
        private final long sequence;
        private final AtomicLong nextDueMs;
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile long claimedDueMs;

        private Registration(AgentTickScheduler owner,
                             AgentSessionId sessionId,
                             AgentRuntimeEntry entry,
                             Runnable tick,
                             long periodMs,
                             long firstDueMs,
                             long sequence) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.entry = entry;
            this.tick = tick;
            this.periodMs = periodMs;
            this.nextDueMs = new AtomicLong(firstDueMs);
            this.sequence = sequence;
        }

        private long sequence() {
            return sequence;
        }

        private boolean isDue(long now) {
            return !cancelled.get() && !paused.get() && now >= nextDueMs.get();
        }

        private boolean prepare(long now) {
            if (cancelled.get() || paused.get()
                    || entry.actionMailbox().isClosed()
                    || !sessionId.matches(entry)
                    || !AgentRuntimeRegistry.isActiveSession(entry, sessionId.generation())
                    || AgentRuntimeIdentityRuntime.bot(entry) == null) {
                return false;
            }
            long due = nextDueMs.get();
            if (now < due || !nextDueMs.compareAndSet(due, nextDueAfter(due, now, periodMs))) {
                return false;
            }
            claimedDueMs = due;
            return true;
        }

        private static long nextDueAfter(long due, long now, long periodMs) {
            long missedPeriods = Math.max(0L, (now - due) / periodMs);
            return due + (missedPeriods + 1L) * periodMs;
        }

        @Override
        public AgentSessionId sessionId() {
            return sessionId;
        }

        @Override
        public AgentSchedulerMode mode() {
            return AgentSchedulerMode.CENTRAL_SEQUENTIAL;
        }

        @Override
        public boolean wake() {
            if (cancelled.get() || paused.get() || !sessionId.matches(entry)) {
                return false;
            }
            nextDueMs.accumulateAndGet(owner.nowMs.getAsLong(), Math::min);
            owner.queueWake();
            return true;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Math.max(0L, nextDueMs.get() - owner.nowMs.getAsLong()), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            completion.complete(null);
            owner.unregister(this);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get();
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return completion.get();
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return completion.get(timeout, unit);
        }
    }
}
