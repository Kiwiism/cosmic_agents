package server.agents.runtime.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.monitoring.AgentSchedulerRegistrationSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.simulation.AgentBackgroundExecutionPolicy;
import server.agents.runtime.simulation.AgentBackgroundOutcomeReconciler;
import server.agents.runtime.simulation.AgentDefaultSimulationPolicy;
import server.agents.runtime.simulation.AgentMaterializationService;
import server.agents.runtime.simulation.AgentSimulationMode;
import server.agents.runtime.simulation.AgentSimulationScheduleDecision;
import server.agents.runtime.simulation.AgentSimulationSchedulePolicy;
import server.agents.runtime.simulation.AgentSimulationTransitionService;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
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
    private final LongSupplier nanoTime;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler;
    private final BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler;
    private final AgentSchedulerConfig config;
    private final AgentSimulationSchedulePolicy simulationSchedulePolicy;
    private final AgentLoadSheddingController loadSheddingController;
    private final AgentSchedulerShard<Registration> shard;
    private final int shardId;
    private int lastSuppressedReadyCount;
    private volatile ScheduledFuture<?> centralTask;
    private volatile boolean acceptingRegistrations = true;

    public record ShutdownResult(int registrations,
                                 int cancelled,
                                 int remaining,
                                 boolean timedOut) {
    }

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
        this(nowMs, System::nanoTime, loopScheduler, wakeScheduler, config, 0);
    }

    AgentTickScheduler(LongSupplier nowMs,
                       LongSupplier nanoTime,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler,
                       AgentSchedulerConfig config) {
        this(nowMs, nanoTime, loopScheduler, wakeScheduler, config, 0);
    }

    AgentTickScheduler(LongSupplier nowMs,
                       LongSupplier nanoTime,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler,
                       AgentSchedulerConfig config,
                       int shardId) {
        this(
                nowMs,
                nanoTime,
                loopScheduler,
                wakeScheduler,
                config,
                shardId,
                defaultSimulationSchedulePolicy(config));
    }

    AgentTickScheduler(LongSupplier nowMs,
                       LongSupplier nanoTime,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler,
                       AgentSchedulerConfig config,
                       int shardId,
                       AgentSimulationSchedulePolicy simulationSchedulePolicy) {
        this(
                nowMs,
                nanoTime,
                loopScheduler,
                wakeScheduler,
                config,
                shardId,
                simulationSchedulePolicy,
                new AgentLoadSheddingController(shardId, AgentLoadSheddingConfig.fromSystemProperties()));
    }

    AgentTickScheduler(LongSupplier nowMs,
                       LongSupplier nanoTime,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> loopScheduler,
                       BiFunction<Runnable, Long, ScheduledFuture<?>> wakeScheduler,
                       AgentSchedulerConfig config,
                       int shardId,
                       AgentSimulationSchedulePolicy simulationSchedulePolicy,
                       AgentLoadSheddingController loadSheddingController) {
        if (nowMs == null || nanoTime == null || loopScheduler == null || wakeScheduler == null || config == null) {
            throw new IllegalArgumentException("Agent scheduler dependencies are required");
        }
        if (simulationSchedulePolicy == null || loadSheddingController == null) {
            throw new IllegalArgumentException("Agent scheduler policies are required");
        }
        this.nowMs = nowMs;
        this.nanoTime = nanoTime;
        this.loopScheduler = loopScheduler;
        this.wakeScheduler = wakeScheduler;
        this.config = config;
        this.simulationSchedulePolicy = simulationSchedulePolicy;
        this.loadSheddingController = loadSheddingController;
        this.shardId = Math.max(0, shardId);
        this.shard = new AgentSchedulerShard<>(
                config.ingressCapacityPerShard(),
                Comparator.comparingLong(Registration::nextDueMs)
                        .thenComparingLong(Registration::sequence));
    }

    public AgentScheduleHandle register(AgentRuntimeEntry entry, Runnable tick, long periodMs) {
        return register(
                entry,
                tick,
                periodMs,
                AgentWorkClass.PRESENTATION_GAMEPLAY,
                AgentPriorityClass.VISIBLE);
    }

    AgentScheduleHandle register(AgentRuntimeEntry entry,
                                 Runnable tick,
                                 long periodMs,
                                 AgentWorkClass workClass,
                                 AgentPriorityClass priority) {
        if (entry == null || tick == null) {
            throw new IllegalArgumentException("Agent entry and tick are required");
        }
        if (workClass == null || priority == null) {
            throw new IllegalArgumentException("Agent work class and priority are required");
        }

        Registration registration;
        boolean replaced;
        synchronized (lifecycleLock) {
            if (!acceptingRegistrations) {
                throw new RejectedExecutionException("Agent scheduler is stopping");
            }
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
                    nextSequence.incrementAndGet(),
                    workClass,
                    priority);
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
        if (!acceptingRegistrations) {
            return;
        }
        if (!ticking.compareAndSet(false, true)) {
            AgentSchedulerMetrics.recordSkipped(registrations.size());
            return;
        }
        long cycleStarted = nanoTime.getAsLong();
        AgentCycleBudget budget = new AgentCycleBudget(cycleStarted, config);
        Map<Integer, Integer> backgroundMapWork = new HashMap<>();
        int consecutiveMapDeferrals = 0;
        int consecutiveSheddingDeferrals = 0;
        boolean continuationNeeded = false;
        boolean budgetLimited = false;
        boolean onlySheddingDeferred = false;
        try {
            shard.drainIngress(this::synchronizeRegistration);
            long now = nowMs.getAsLong();
            moveDueToReady(now);
            if (loadSheddingController.sampleDue(now)) {
                int actionableReady = Math.max(0, shard.readyCount() - lastSuppressedReadyCount);
                loadSheddingController.evaluate(new AgentSchedulerPressureSample(
                        now,
                        AgentSchedulerMetrics.snapshot().queueLagP95Ms(),
                        registrations.size(),
                        shard.ingressDepth(),
                        config.ingressCapacityPerShard(),
                        actionableReady,
                        loadSheddingController.enabled()
                                ? loadSheddingController.sampleServerHealth()
                                : AgentServerHealthSnapshot.healthy()));
            }
            lastSuppressedReadyCount = 0;
            while (shard.readyCount() > 0) {
                long selectionTimeNs = nanoTime.getAsLong();
                if (budget.exhausted(selectionTimeNs)) {
                    continuationNeeded = true;
                    budgetLimited = true;
                    break;
                }
                boolean criticalReady = shard.hasReady(
                        AgentPriorityClass.CRITICAL.ordinal(),
                        registration -> registration.effectivePriority(
                                now, config.starvationPromotionMs()).ordinal());
                boolean visibleReady = shard.hasReady(
                        AgentPriorityClass.VISIBLE.ordinal(),
                        registration -> registration.effectivePriority(
                                now, config.starvationPromotionMs()).ordinal());
                int maximumPriority = budget.preferredMaximumPriority(criticalReady, visibleReady);
                Registration registration = shard.pollReady(
                        maximumPriority,
                        candidate -> candidate.effectivePriority(
                                now, config.starvationPromotionMs()).ordinal(),
                        Comparator.comparingLong(Registration::readyDueMs)
                                .thenComparingLong(Registration::sequence));
                if (registration == null) {
                    break;
                }
                AgentPriorityClass effectivePriority =
                        registration.effectivePriority(now, config.starvationPromotionMs());
                registration.applyLoadShedding(loadSheddingController);
                if (!registration.isLifecycleCritical()
                        && registration.entry.actionMailbox().size() == 0
                        && !loadSheddingController.allows(
                        registration.workClass,
                        effectivePriority,
                        registration.simulationMode)) {
                    shard.addReady(registration, registration.priority);
                    lastSuppressedReadyCount++;
                    consecutiveSheddingDeferrals++;
                    AgentSchedulerMetrics.recordLoadSheddingSuppressed(loadSheddingController.primaryReason());
                    if (consecutiveSheddingDeferrals >= shard.readyCount()) {
                        onlySheddingDeferred = true;
                        break;
                    }
                    continue;
                }
                consecutiveSheddingDeferrals = 0;
                if (!admitsBackgroundMapWork(registration, backgroundMapWork)) {
                    shard.addReady(registration, registration.priority);
                    continuationNeeded = true;
                    AgentSchedulerMetrics.recordMapBudgetDeferral();
                    consecutiveMapDeferrals++;
                    if (consecutiveMapDeferrals >= shard.readyCount()) {
                        break;
                    }
                    continue;
                }
                consecutiveMapDeferrals = 0;
                if (!budget.admits(effectivePriority, registration.estimatedCostNs(), selectionTimeNs)) {
                    shard.addReadyFirst(registration, registration.priority);
                    continuationNeeded = true;
                    budgetLimited = true;
                    break;
                }
                registration.clearReady();
                recordBackgroundMapWork(registration, backgroundMapWork);
                long elapsedNs = update(registration, now, config);
                budget.record(effectivePriority, elapsedNs);
                if (registration.entry.tickSliceState().continuationPending()) {
                    registration.requestImmediateContinuation(now);
                    continuationNeeded = true;
                }
                if (shouldSchedule(registration)) {
                    shard.addOrUpdate(registration);
                }
            }
            int deferred = shard.readyCount();
            if (deferred > 0 && !onlySheddingDeferred) {
                continuationNeeded = true;
                AgentSchedulerMetrics.recordDeferred(deferred);
                AgentSchedulerMetrics.recordSkipped(deferred);
            }
            long cycleNowNs = nanoTime.getAsLong();
            if (deferred > 0 && (budgetLimited || budget.deadlineExceeded(cycleNowNs))) {
                AgentSchedulerMetrics.recordBudgetExhausted();
            }
            if (config.mode() == AgentSchedulerMode.CENTRAL_SHARDED) {
                AgentSchedulerMetrics.recordShardDepths(
                        shardId,
                        registrations.size(),
                        shard.ingressDepth(),
                        shard.scheduledCount(),
                        shard.readyCount(),
                        shard.readyDepths());
            } else {
                AgentSchedulerMetrics.recordDepths(
                        shard.ingressDepth(),
                        shard.ingressHighWaterMark(),
                        shard.scheduledCount(),
                        shard.readyCount(),
                        shard.readyDepths());
            }
        } finally {
            ticking.set(false);
            AgentSchedulerMetrics.recordCycle(Math.max(0L, nanoTime.getAsLong() - cycleStarted));
            if (acceptingRegistrations) {
                stopCentralTaskIfIdle();
                if (continuationNeeded) {
                    queueWake();
                }
            } else {
                clearStoppedState();
            }
        }
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (ticking.get() || !registrations.isEmpty() || !ownedRegistrations.isEmpty() || !shard.isIdle()) {
                throw new IllegalStateException("Agent scheduler still owns state from its previous lifecycle");
            }
            acceptingRegistrations = true;
        }
    }

    public ShutdownResult shutdownAndDrain(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent scheduler shutdown timeout must not be negative");
        }
        int registrationsAtStart;
        synchronized (lifecycleLock) {
            acceptingRegistrations = false;
            ScheduledFuture<?> task = centralTask;
            centralTask = null;
            if (task != null) {
                task.cancel(false);
            }
            registrationsAtStart = ownedRegistrations.size();
            ownedRegistrations.keySet().forEach(Registration::cancelWithoutSync);
            registrations.clear();
            wakeQueued.set(false);
        }

        long deadline = System.nanoTime() + Math.max(0L, timeout.toNanos());
        while (ticking.get() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Math.min(1_000_000L, Math.max(1L, deadline - System.nanoTime())));
        }
        boolean timedOut = ticking.get();
        if (!timedOut) {
            clearStoppedState();
        }
        return new ShutdownResult(
                registrationsAtStart,
                registrationsAtStart,
                ownedRegistrations.size(),
                timedOut);
    }

    public boolean acceptingRegistrations() {
        return acceptingRegistrations;
    }

    private void moveDueToReady(long now) {
        while (true) {
            Registration registration = shard.peekDue();
            if (registration == null || registration.nextDueMs() > now) {
                return;
            }
            shard.pollDue();
            if (shouldSchedule(registration)) {
                registration.refreshSchedulingPolicy(now);
                registration.markReady(now);
                shard.addReady(registration, registration.priority);
            }
        }
    }

    private boolean admitsBackgroundMapWork(Registration registration, Map<Integer, Integer> workByMap) {
        int limit = config.backgroundMaxWorkPerMapPerCycle();
        return limit == 0
                || registration.simulationMode == AgentSimulationMode.PRESENTATION
                || workByMap.getOrDefault(registration.mapId(), 0) < limit;
    }

    private static void recordBackgroundMapWork(Registration registration, Map<Integer, Integer> workByMap) {
        if (registration.simulationMode != AgentSimulationMode.PRESENTATION) {
            workByMap.merge(registration.mapId(), 1, Integer::sum);
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

    public List<AgentSchedulerRegistrationSnapshot> registrationSnapshots() {
        return registrations.values().stream()
                .filter(registration -> !registration.cancelled.get())
                .map(Registration::snapshot)
                .sorted(Comparator
                        .comparingInt((AgentSchedulerRegistrationSnapshot snapshot) ->
                                snapshot.sessionId().agentCharacterId())
                        .thenComparingLong(snapshot -> snapshot.sessionId().generation()))
                .toList();
    }

    int ownedRegistrationCount() {
        return ownedRegistrations.size();
    }

    int scheduledRegistrationCount() {
        return shard.scheduledCount();
    }

    int readyRegistrationCount() {
        return shard.readyCount();
    }

    int ingressDepth() {
        return shard.ingressDepth();
    }

    int ingressHighWaterMark() {
        return shard.ingressHighWaterMark();
    }

    /* The registration callback owns either one compact tick or one bounded frame turn. */
    private long update(Registration registration, long now, AgentSchedulerConfig config) {
        if (!registration.quiescence.checkLiveness() || !registration.prepare(now)) {
            AgentSchedulerMetrics.recordSkipped(1);
            return 0L;
        }
        long started = nanoTime.getAsLong();
        long elapsedNs;
        AgentQuiescenceController.ExecutionMode executionMode = registration.quiescence.beforeExecution();
        if (executionMode == AgentQuiescenceController.ExecutionMode.SKIP) {
            return 0L;
        }
        try {
            if (executionMode == AgentQuiescenceController.ExecutionMode.QUIESCENCE_MAINTENANCE) {
                registration.quiescence.runMaintenance();
            } else {
                registration.tick.run();
            }
        } catch (Throwable failure) {
            AgentSchedulerMetrics.recordFailure();
            log.warn("Central Agent scheduler tick failed for session {}", registration.sessionId, failure);
        } finally {
            registration.quiescence.afterExecution();
            elapsedNs = Math.max(0L, nanoTime.getAsLong() - started);
            registration.recordCost(elapsedNs);
            boolean slow = elapsedNs >= TimeUnit.MILLISECONDS.toNanos(config.slowTickMs());
            AgentSchedulerMetrics.recordUpdated(
                    now - registration.claimedDueMs,
                    elapsedNs,
                    registration.workClass,
                    registration.simulationMode,
                    slow);
            if (slow && config.logSlowTicks()) {
                log.warn("Slow central Agent tick session={} elapsedMs={}",
                        registration.sessionId, elapsedNs / 1_000_000L);
            }
        }
        return elapsedNs;
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
            registration.refreshSchedulingPolicy(nowMs.getAsLong());
            if (shard.containsReady(registration)) {
                shard.updateReadyPriority(registration, registration.priority);
            } else {
                shard.addOrUpdate(registration);
            }
        }
    }

    private boolean shouldSchedule(Registration registration) {
        return !registration.cancelled.get()
                && !registration.quiescence.quiescent()
                && (!registration.paused.get() || registration.quiescence.requested())
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
        if (!acceptingRegistrations) {
            throw new RejectedExecutionException("Agent scheduler is stopping");
        }
        if (centralTask == null || centralTask.isCancelled() || centralTask.isDone()) {
            centralTask = loopScheduler.apply(this::tickAll, config.baseTickMs());
        }
    }

    private boolean cancel(Registration registration) {
        synchronized (lifecycleLock) {
            if (!registration.cancelled.compareAndSet(false, true)) {
                return false;
            }
            registration.quiescence.close();
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
                    || (registration.paused.get() && !registration.quiescence.requested())
                    || registration.quiescence.quiescent()
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
                AgentLoadSheddingRuntime.clearShard(shardId);
            }
        }
    }

    private void queueWake() {
        if (!acceptingRegistrations) {
            return;
        }
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

    private void clearStoppedState() {
        synchronized (lifecycleLock) {
            if (ticking.get()) {
                return;
            }
            ownedRegistrations.keySet().forEach(Registration::cancelWithoutSync);
            registrations.clear();
            ownedRegistrations.clear();
            shard.clear();
            wakeQueued.set(false);
            AgentLoadSheddingRuntime.clearShard(shardId);
            if (config.mode() == AgentSchedulerMode.CENTRAL_SHARDED) {
                AgentSchedulerMetrics.recordShardDepths(shardId, 0, 0, 0, 0, Map.of());
            } else {
                AgentSchedulerMetrics.recordDepths(0, shard.ingressHighWaterMark(), 0, 0, Map.of());
            }
        }
    }

    private static AgentSimulationSchedulePolicy defaultSimulationSchedulePolicy(AgentSchedulerConfig config) {
        return new AgentSimulationSchedulePolicy(
                config,
                new AgentDefaultSimulationPolicy(
                        config.simulationEnabled(),
                        config.backgroundAbstractEnabled(),
                        AgentMapGatewayRuntime.map(),
                        AgentBackgroundExecutionPolicy.denyAll()),
                new AgentSimulationTransitionService(
                        AgentMaterializationService.validating(),
                        AgentBackgroundOutcomeReconciler.noPendingOutcomes()));
    }

    private static final class Registration implements AgentScheduleHandle {
        private final AgentTickScheduler owner;
        private final AgentSessionId sessionId;
        private final AgentRuntimeEntry entry;
        private final Runnable tick;
        private final long basePeriodMs;
        private final long sequence;
        private final AgentWorkClass baseWorkClass;
        private final AgentPriorityClass basePriority;
        private final AtomicLong pendingWakeDueMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicBoolean ingressQueued = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AgentQuiescenceController quiescence;
        private volatile long nextDueMs;
        private volatile long claimedDueMs;
        private long simulationPeriodMs;
        private long periodMs;
        private volatile AgentWorkClass workClass;
        private volatile AgentPriorityClass priority;
        private volatile AgentSimulationMode simulationMode = AgentSimulationMode.PRESENTATION;
        private volatile long readySinceMs = -1L;
        private long readyDueMs;
        private volatile long estimatedCostNs = 100_000L;
        private boolean costObserved;
        private int recordedPromotionLevels;

        private Registration(AgentTickScheduler owner,
                             AgentSessionId sessionId,
                             AgentRuntimeEntry entry,
                             Runnable tick,
                             long periodMs,
                             long firstDueMs,
                             long sequence,
                             AgentWorkClass workClass,
                             AgentPriorityClass priority) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.entry = entry;
            this.tick = tick;
            this.basePeriodMs = periodMs;
            this.nextDueMs = firstDueMs;
            this.sequence = sequence;
            this.baseWorkClass = workClass;
            this.basePriority = priority;
            this.quiescence = AgentScheduler.controller(entry, sessionId, owner.nowMs);
            this.simulationPeriodMs = periodMs;
            this.periodMs = periodMs;
            this.workClass = workClass;
            this.priority = priority;
        }

        private long sequence() {
            return sequence;
        }

        private long nextDueMs() {
            return nextDueMs;
        }

        private long readyDueMs() {
            return readyDueMs;
        }

        private void markReady(long now) {
            if (readySinceMs < 0L) {
                readySinceMs = now;
                readyDueMs = nextDueMs;
                recordedPromotionLevels = 0;
            }
        }

        private void clearReady() {
            readySinceMs = -1L;
            recordedPromotionLevels = 0;
        }

        private AgentPriorityClass effectivePriority(long now, long promotionMs) {
            if (readySinceMs < 0L) {
                return priority;
            }
            long waitedMs = Math.max(0L, now - readySinceMs);
            int promotionLevels = (int) Math.min(
                    priority.maximumPromotionLevels(),
                    waitedMs / promotionMs);
            if (promotionLevels > recordedPromotionLevels) {
                AgentSchedulerMetrics.recordStarvationPromotions(promotionLevels - recordedPromotionLevels);
                recordedPromotionLevels = promotionLevels;
            }
            return priority.promoted(promotionLevels);
        }

        private long estimatedCostNs() {
            return estimatedCostNs;
        }

        private int mapId() {
            return AgentRuntimeIdentityRuntime.botMapId(entry);
        }

        private AgentSchedulerRegistrationSnapshot snapshot() {
            return new AgentSchedulerRegistrationSnapshot(
                    sessionId,
                    nextDueMs,
                    estimatedCostNs,
                    workClass,
                    priority,
                    simulationMode,
                    readySinceMs >= 0L,
                    paused.get(),
                    quiescence.quiescent());
        }

        private boolean isLifecycleCritical() {
            return baseWorkClass == AgentWorkClass.LIFECYCLE_CRITICAL
                    || basePriority == AgentPriorityClass.CRITICAL;
        }

        private void recordCost(long elapsedNs) {
            long sample = Math.max(1L, elapsedNs);
            if (!costObserved) {
                estimatedCostNs = sample;
                costObserved = true;
                return;
            }
            estimatedCostNs = (estimatedCostNs * 3L + sample) / 4L;
        }

        private void refreshSchedulingPolicy(long now) {
            try {
                AgentSimulationScheduleDecision decision = owner.simulationSchedulePolicy.decide(
                        entry,
                        basePeriodMs,
                        baseWorkClass,
                        basePriority,
                        now);
                simulationMode = decision.mode();
                simulationPeriodMs = decision.periodMs();
                periodMs = simulationPeriodMs;
                workClass = decision.workClass();
                priority = decision.priority();
            } catch (Throwable failure) {
                simulationMode = AgentSimulationMode.PRESENTATION;
                simulationPeriodMs = basePeriodMs;
                periodMs = basePeriodMs;
                workClass = baseWorkClass;
                priority = basePriority;
                AgentSchedulerMetrics.recordFailure();
                log.warn("Agent simulation policy failed for session {}; using presentation schedule",
                        sessionId, failure);
            }
        }

        private void applyLoadShedding(AgentLoadSheddingController controller) {
            periodMs = controller.effectivePeriodMs(simulationPeriodMs, simulationMode);
        }

        private boolean prepare(long now) {
            if (cancelled.get()
                    || quiescence.quiescent()
                    || (paused.get() && !quiescence.requested())
                    || now < nextDueMs) {
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

        private void requestImmediateContinuation(long dueMs) {
            if (dueMs < nextDueMs) {
                nextDueMs = dueMs;
            }
        }

        private void applyPendingWake() {
            long requestedDueMs = pendingWakeDueMs.getAndSet(Long.MAX_VALUE);
            if (requestedDueMs < nextDueMs) {
                nextDueMs = requestedDueMs;
            }
        }

        private void cancelForReplacement() {
            if (cancelled.compareAndSet(false, true)) {
                quiescence.close();
                completion.complete(null);
                owner.requireSync(this);
            }
        }

        private void cancelWithoutSync() {
            cancelled.set(true);
            quiescence.close();
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
            return owner.config.mode();
        }

        @Override
        public boolean wake() {
            return owner.wake(this);
        }

        @Override
        public CompletionStage<AgentQuiescenceToken> quiesce(
                AgentQuiescenceReason reason,
                Duration timeout) {
            CompletionStage<AgentQuiescenceToken> result;
            synchronized (owner.lifecycleLock) {
                if (cancelled.get() || owner.registrations.get(entry) != this) {
                    return CompletableFuture.failedFuture(new AgentQuiescenceException(
                            AgentQuiescenceException.Reason.CLOSED,
                            "Agent scheduler registration is closed"));
                }
                result = quiescence.request(reason, timeout);
                if (quiescence.requested()) {
                    requestWake(owner.nowMs.getAsLong());
                    owner.requireSync(this);
                }
            }
            if (quiescence.requested()) {
                owner.queueWake();
            }
            return result;
        }

        @Override
        public boolean resume(AgentQuiescenceToken token) {
            boolean resumed;
            synchronized (owner.lifecycleLock) {
                resumed = quiescence.resume(token);
                if (resumed) {
                    requestWake(owner.nowMs.getAsLong());
                    owner.requireSync(this);
                }
            }
            if (resumed) {
                owner.queueWake();
            }
            return resumed;
        }

        @Override
        public boolean validatesQuiescence(AgentQuiescenceToken token) {
            return quiescence.validates(token);
        }

        @Override
        public boolean isQuiescent() {
            return quiescence.quiescent();
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
