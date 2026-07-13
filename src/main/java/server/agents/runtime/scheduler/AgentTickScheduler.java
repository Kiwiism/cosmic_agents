package server.agents.runtime.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
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
    private final Map<Registration, Boolean> ownedRegistrations = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong();
    private final AtomicBoolean ticking = new AtomicBoolean();
    private final AtomicBoolean wakeQueued = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final LongSupplier nowMs;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler;
    private final AgentSchedulerConfig config;
    private final AgentSchedulerShard<Registration> shard;
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
        if (nowMs == null || loopScheduler == null || wakeScheduler == null || config == null) {
            throw new IllegalArgumentException("Agent scheduler dependencies are required");
        }
        this.nowMs = nowMs;
        this.loopScheduler = loopScheduler;
        this.wakeScheduler = wakeScheduler;
        this.config = config;
        this.shard = new AgentSchedulerShard<>(
                config.ingressCapacityPerShard(),
                Comparator.comparingLong(Registration::nextDueMs)
                        .thenComparingLong(Registration::sequence));
    }

    public AgentScheduleHandle register(AgentRuntimeEntry entry, Runnable tick, long periodMs) {
        if (entry == null || tick == null) {
            throw new IllegalArgumentException("Agent entry and tick are required");
        }

        Registration registration;
        boolean replaced;
        synchronized (lifecycleLock) {
            if (ownedRegistrations.size() >= config.ingressCapacityPerShard()) {
                throw new RejectedExecutionException(
                        "Agent scheduler registration capacity is full: " + config.ingressCapacityPerShard());
            }
            ensureCentralTaskLocked();
            registration = new Registration(
                    this,
                    AgentSessionId.from(entry),
                    entry,
                    tick,
                    Math.max(1L, periodMs),
                    nowMs.getAsLong(),
                    nextSequence.incrementAndGet());
            Registration previous = registrations.put(entry, registration);
            ownedRegistrations.put(registration, Boolean.TRUE);
            replaced = previous != null;
            if (previous != null) {
                previous.cancelForReplacement();
            }
            if (!requestSync(registration)) {
                registrations.remove(entry, registration);
                ownedRegistrations.remove(registration);
                registration.cancelWithoutSync();
                throw new RejectedExecutionException("Agent scheduler ingress is full");
            }
        }
        if (replaced) {
            queueWake();
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
            shard.drainIngress(this::synchronizeRegistration);
            long now = nowMs.getAsLong();
            int scheduledAtStart = shard.scheduledCount();
            int configuredLimit = config.maxAgentsPerTick();
            int workLimit = configuredLimit == 0
                    ? scheduledAtStart
                    : Math.min(configuredLimit, scheduledAtStart);
            int processed = 0;
            while (processed < workLimit) {
                Registration registration = shard.peekDue();
                if (registration == null || registration.nextDueMs() > now) {
                    break;
                }
                shard.pollDue();
                processed++;
                update(registration, now, config);
                if (shouldSchedule(registration)) {
                    shard.addOrUpdate(registration);
                }
            }
            Registration remainingDue = shard.peekDue();
            if (configuredLimit > 0 && remainingDue != null && remainingDue.nextDueMs() <= now) {
                AgentSchedulerMetrics.recordSkipped(1);
            }
        } finally {
            ticking.set(false);
            AgentSchedulerMetrics.recordCycle(System.nanoTime() - cycleStarted);
            stopCentralTaskIfIdle();
        }
    }

    public void pause(AgentRuntimeEntry entry) {
        synchronized (lifecycleLock) {
            Registration registration = registrations.get(entry);
            if (registration != null && registration.paused.compareAndSet(false, true)) {
                requireSync(registration);
            }
        }
    }

    public void resume(AgentRuntimeEntry entry) {
        boolean resumed = false;
        synchronized (lifecycleLock) {
            Registration registration = registrations.get(entry);
            if (registration != null && registration.paused.compareAndSet(true, false)) {
                registration.requestWake(nowMs.getAsLong());
                requireSync(registration);
                resumed = true;
            }
        }
        if (resumed) {
            queueWake();
        }
    }

    public int registrationCount() {
        return registrations.size();
    }

    int ownedRegistrationCount() {
        return ownedRegistrations.size();
    }

    int scheduledRegistrationCount() {
        return shard.scheduledCount();
    }

    int ingressDepth() {
        return shard.ingressDepth();
    }

    int ingressHighWaterMark() {
        return shard.ingressHighWaterMark();
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

    private void synchronizeRegistration(Registration registration) {
        registration.ingressQueued.set(false);
        registration.applyPendingWake();
        synchronized (lifecycleLock) {
            if (!shouldSchedule(registration)) {
                shard.remove(registration);
                retireIfClosed(registration);
                return;
            }
            shard.addOrUpdate(registration);
        }
    }

    private boolean shouldSchedule(Registration registration) {
        return !registration.cancelled.get()
                && !registration.paused.get()
                && registrations.get(registration.entry) == registration;
    }

    private void retireIfClosed(Registration registration) {
        if ((registration.cancelled.get() || registrations.get(registration.entry) != registration)
                && !registration.ingressQueued.get()) {
            ownedRegistrations.remove(registration);
        }
    }

    private boolean requestSync(Registration registration) {
        if (!registration.ingressQueued.compareAndSet(false, true)) {
            return true;
        }
        if (shard.offer(registration)) {
            return true;
        }
        registration.ingressQueued.set(false);
        return false;
    }

    private void requireSync(Registration registration) {
        if (!requestSync(registration)) {
            throw new IllegalStateException("Admitted Agent scheduler registration lost its ingress capacity");
        }
    }

    private void ensureCentralTaskLocked() {
        if (centralTask == null || centralTask.isCancelled() || centralTask.isDone()) {
            centralTask = loopScheduler.apply(this::tickAll, config.baseTickMs());
        }
    }

    private boolean cancel(Registration registration) {
        synchronized (lifecycleLock) {
            if (!registration.cancelled.compareAndSet(false, true)) {
                return false;
            }
            registration.completion.complete(null);
            registrations.remove(registration.entry, registration);
            requireSync(registration);
        }
        queueWake();
        return true;
    }

    private boolean wake(Registration registration) {
        synchronized (lifecycleLock) {
            if (registration.cancelled.get()
                    || registration.paused.get()
                    || registrations.get(registration.entry) != registration
                    || !registration.sessionId.matches(registration.entry)) {
                return false;
            }
            registration.requestWake(nowMs.getAsLong());
            requireSync(registration);
        }
        queueWake();
        return true;
    }

    private void stopCentralTaskIfIdle() {
        synchronized (lifecycleLock) {
            if (registrations.isEmpty()
                    && ownedRegistrations.isEmpty()
                    && shard.isIdle()
                    && centralTask != null) {
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
        private final AtomicLong pendingWakeDueMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicBoolean ingressQueued = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile long nextDueMs;
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
            this.nextDueMs = firstDueMs;
            this.sequence = sequence;
        }

        private long sequence() {
            return sequence;
        }

        private long nextDueMs() {
            return nextDueMs;
        }

        private boolean prepare(long now) {
            if (cancelled.get() || paused.get() || now < nextDueMs) {
                return false;
            }
            long due = nextDueMs;
            nextDueMs = nextDueAfter(due, now, periodMs);
            if (entry.actionMailbox().isClosed()
                    || !sessionId.matches(entry)
                    || !AgentRuntimeRegistry.isActiveSession(entry, sessionId.generation())
                    || AgentRuntimeIdentityRuntime.bot(entry) == null) {
                return false;
            }
            claimedDueMs = due;
            return true;
        }

        private void requestWake(long dueMs) {
            pendingWakeDueMs.accumulateAndGet(dueMs, Math::min);
        }

        private void applyPendingWake() {
            long requestedDueMs = pendingWakeDueMs.getAndSet(Long.MAX_VALUE);
            if (requestedDueMs < nextDueMs) {
                nextDueMs = requestedDueMs;
            }
        }

        private void cancelForReplacement() {
            if (cancelled.compareAndSet(false, true)) {
                completion.complete(null);
                owner.requireSync(this);
            }
        }

        private void cancelWithoutSync() {
            cancelled.set(true);
            completion.complete(null);
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
            return owner.wake(this);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Math.max(0L, nextDueMs - owner.nowMs.getAsLong()), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return owner.cancel(this);
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
