package server.agents.plans.mapleisland.cohort;

import config.YamlConfig;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Serialized wave scheduler. Timer callbacks only hand work to the external cohort worker. */
public final class MapleIslandCohortRunService {
    public static final int DEFAULT_MAX_TOTAL = 500;
    public static final int ABSOLUTE_MAX_TOTAL = 2_000;
    public static final int MAX_BATCH = 10;
    public static final int MIN_INTERVAL_SECONDS = 5;
    public static final int MAX_INTERVAL_SECONDS = 3_600;
    private static final long MONITOR_INTERVAL_MS = 5_000L;
    private static final long TERMINAL_FAILURE_GRACE_MS = 30_000L;
    private static final long DEFAULT_STALL_TIMEOUT_MS = 30L * 60L * 1_000L;
    private static final long WAVE_JITTER_DOMAIN = 0x574156452D4A4954L;

    public record Shard(int world, int channel) {
        public Shard {
            if (world < 0 || channel <= 0) {
                throw new IllegalArgumentException("Invalid Maple Island cohort shard");
            }
        }
    }

    public record StartRequest(int ownerCharacterId,
                               int world,
                               int channel,
                               int total,
                               int batch,
                               int intervalSeconds,
                               Long requestedSeed,
                               MapleIslandCohortRealismMode realismMode) {
        public StartRequest(int ownerCharacterId,
                            int world,
                            int channel,
                            int total,
                            int batch,
                            int intervalSeconds,
                            Long requestedSeed) {
            this(ownerCharacterId, world, channel, total, batch, intervalSeconds,
                    requestedSeed, MapleIslandCohortRealismMode.LIGHT);
        }

        public StartRequest {
            if (ownerCharacterId <= 0) {
                throw new IllegalArgumentException("A controller character is required");
            }
            if (total < 1 || total > ABSOLUTE_MAX_TOTAL) {
                throw new IllegalArgumentException("total must be between 1 and " + ABSOLUTE_MAX_TOTAL);
            }
            if (batch < 1 || batch > MAX_BATCH || batch > total) {
                throw new IllegalArgumentException("batch must be between 1 and min(total, " + MAX_BATCH + ")");
            }
            if (intervalSeconds < MIN_INTERVAL_SECONDS || intervalSeconds > MAX_INTERVAL_SECONDS) {
                throw new IllegalArgumentException("interval must be between " + MIN_INTERVAL_SECONDS
                        + " and " + MAX_INTERVAL_SECONDS + " seconds");
            }
            new Shard(world, channel);
            realismMode = realismMode == null ? MapleIslandCohortRealismMode.LIGHT : realismMode;
        }
    }

    public record AgentContext(String sessionId,
                               long runSeed,
                               int ordinal,
                               int ownerCharacterId,
                               int world,
                               int channel,
                               MapleIslandCohortRealismMode realismMode) {
    }

    public enum RunState {
        RELEASING,
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_FAILURES,
        STALLED,
        CANCELLED,
        STOPPING,
        STOPPED,
        FAILED
    }

    public enum AgentState {
        RUNNING,
        COMPLETED,
        FAILED,
        MISSING
    }

    public record Status(String sessionId,
                         RunState state,
                         int requested,
                         int batch,
                         int intervalSeconds,
                         long runSeed,
                         MapleIslandCohortRealismMode realismMode,
                         int launched,
                         int failedStarts,
                         int running,
                         int completed,
                         int failedRuns,
                         int missing,
                         int pending,
                         int admissionDeferrals,
                         String lastError) {
    }

    public interface Hooks {
        List<Agent> acquire(int count,
                            String sessionId,
                            int ownerCharacterId,
                            int world,
                            int channel,
                            Set<Integer> excludedCharacterIds) throws Exception;

        void startAgent(Agent agent, AgentContext context) throws Exception;

        void markBroken(Agent agent, String sessionId, String error) throws Exception;

        AgentState agentState(int characterId);

        void stopAgent(int characterId);

        void releaseSession(String sessionId) throws Exception;

        ScheduledFuture<?> schedule(Runnable action, long delayMs);

        void dispatch(Runnable action);

        default long waveAdmissionDelayMs(int world, int channel, int launched) {
            return 0L;
        }

        default void runTerminated(String sessionId, RunState state) {
        }
    }

    private final Hooks hooks;
    private final int maxTotal;
    private final long stallTimeoutMs;
    private final LongSupplier currentTimeMs;
    private final Map<Shard, Session> sessions = new ConcurrentHashMap<>();

    public MapleIslandCohortRunService(Hooks hooks) {
        this(hooks, configuredMaxTotal(), configuredStallTimeoutMs(), System::currentTimeMillis);
    }

    MapleIslandCohortRunService(Hooks hooks, int maxTotal, long stallTimeoutMs,
                                LongSupplier currentTimeMs) {
        this.hooks = hooks;
        this.maxTotal = Math.clamp(maxTotal, 1, ABSOLUTE_MAX_TOTAL);
        this.stallTimeoutMs = Math.max(MONITOR_INTERVAL_MS, stallTimeoutMs);
        this.currentTimeMs = currentTimeMs;
    }

    public synchronized Status start(StartRequest request) {
        if (request.total() > maxTotal) {
            throw new IllegalArgumentException("total must be between 1 and configured maximum " + maxTotal);
        }
        Shard shard = new Shard(request.world(), request.channel());
        Session existing = sessions.get(shard);
        if (existing != null && existing.state != RunState.STOPPED) {
            throw new IllegalStateException("A Maple Island cohort already exists on world "
                    + shard.world() + " channel " + shard.channel() + "; stop it first");
        }
        long seed = request.requestedSeed() == null
                ? ThreadLocalRandom.current().nextLong()
                : request.requestedSeed();
        String sessionId = "mi-%d-%d-%s-%d".formatted(
                shard.world(), shard.channel(), Long.toUnsignedString(seed, 36), System.currentTimeMillis());
        Session session = new Session(sessionId, request, seed, currentTimeMs.getAsLong());
        sessions.put(shard, session);
        try {
            scheduleWave(session, 0L);
        } catch (RuntimeException failure) {
            sessions.remove(shard, session);
            throw failure;
        }
        return status(session);
    }

    public synchronized Status status(int world, int channel) {
        Session session = sessions.get(new Shard(world, channel));
        return session == null ? null : status(session);
    }

    public synchronized Status cancel(int world, int channel) {
        Session session = requireSession(world, channel);
        if (session.state == RunState.RELEASING || session.state == RunState.RUNNING) {
            session.state = RunState.CANCELLED;
            cancelScheduled(session);
            cancelMonitor(session);
        }
        return status(session);
    }

    public synchronized Status stop(int world, int channel) {
        Session session = requireSession(world, channel);
        if (session.state == RunState.STOPPED || session.state == RunState.STOPPING) {
            return status(session);
        }
        session.state = RunState.STOPPING;
        cancelScheduled(session);
        cancelMonitor(session);
        try {
            hooks.dispatch(() -> stopOnWorker(session));
        } catch (RuntimeException failure) {
            session.state = RunState.FAILED;
            session.lastError = message(failure);
            notifyTerminal(session);
            throw failure;
        }
        return status(session);
    }

    public synchronized Set<String> activeSessionIds() {
        return sessions.values().stream()
                .filter(session -> session.state != RunState.STOPPED)
                .map(session -> session.sessionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void handoffWave(Session session) {
        synchronized (this) {
            if (session.state != RunState.RELEASING || session.waveInFlight) {
                return;
            }
            session.scheduledWave = null;
            long admissionDelayMs = Math.max(0L, hooks.waveAdmissionDelayMs(
                    session.request.world(), session.request.channel(), session.launchedIds.size()));
            if (admissionDelayMs > 0L) {
                session.admissionDeferrals++;
                scheduleWave(session, admissionDelayMs);
                return;
            }
            session.waveInFlight = true;
        }
        try {
            hooks.dispatch(() -> prepareWave(session));
        } catch (RuntimeException failure) {
            synchronized (this) {
                session.waveInFlight = false;
                fail(session, failure);
            }
        }
    }

    private void prepareWave(Session session) {
        int count;
        long[] launchOffsetsMs;
        synchronized (this) {
            if (session.state != RunState.RELEASING) {
                session.waveInFlight = false;
                return;
            }
            count = Math.min(session.request.batch(), session.request.total() - session.launchedIds.size());
            launchOffsetsMs = launchOffsetsMs(session, count);
            session.waveInFlight = false;
        }
        scheduleLaunch(session, launchOffsetsMs, 0, launchOffsetsMs[0]);
    }

    private void handoffLaunch(Session session, long[] launchOffsetsMs, int slot) {
        synchronized (this) {
            if (session.state != RunState.RELEASING || session.waveInFlight) {
                return;
            }
            session.scheduledWave = null;
            session.waveInFlight = true;
        }
        try {
            hooks.dispatch(() -> executeLaunch(session, launchOffsetsMs, slot));
        } catch (RuntimeException failure) {
            synchronized (this) {
                session.waveInFlight = false;
                fail(session, failure);
            }
        }
    }

    private void executeLaunch(Session session, long[] launchOffsetsMs, int slot) {
        Agent leased = null;
        try {
            Set<Integer> excluded;
            synchronized (this) {
                if (session.state != RunState.RELEASING) {
                    return;
                }
                excluded = Set.copyOf(session.attemptedIds);
            }
            List<Agent> agents = hooks.acquire(1, session.sessionId,
                    session.request.ownerCharacterId(), session.request.world(),
                    session.request.channel(), excluded);
            if (agents.isEmpty()) {
                throw new IllegalStateException("No reusable cohort Agent could be leased");
            }
            leased = agents.getFirst();
            AgentContext context;
            boolean cancelled;
            synchronized (this) {
                session.attemptedIds.add(leased.characterId());
                cancelled = session.state != RunState.RELEASING;
                context = cancelled ? null : new AgentContext(
                        session.sessionId, session.runSeed, session.nextOrdinal++,
                        session.request.ownerCharacterId(), session.request.world(),
                        session.request.channel(), session.request.realismMode());
            }
            if (cancelled) {
                hooks.releaseSession(session.sessionId);
                return;
            }
            hooks.startAgent(leased, context);
            synchronized (this) {
                session.launchedIds.add(leased.characterId());
            }
        } catch (Exception failure) {
            synchronized (this) {
                session.failedStarts++;
                session.lastError = message(failure);
            }
            if (leased != null) {
                try {
                    hooks.markBroken(leased, session.sessionId, message(failure));
                } catch (Exception markFailure) {
                    synchronized (this) {
                        session.lastError = "Failed to persist broken lease after "
                                + message(failure) + "; " + message(markFailure);
                        session.state = RunState.FAILED;
                    }
                }
            }
        } finally {
            synchronized (this) {
                session.waveInFlight = false;
                finishLaunch(session, launchOffsetsMs, slot);
            }
        }
    }

    private void finishLaunch(Session session, long[] launchOffsetsMs, int slot) {
        if (session.state == RunState.FAILED) {
            notifyTerminal(session);
            return;
        }
        if (session.state != RunState.RELEASING) {
            return;
        }
        if (slot + 1 < launchOffsetsMs.length) {
            long nextDelayMs = launchOffsetsMs[slot + 1] - launchOffsetsMs[slot];
            scheduleLaunch(session, launchOffsetsMs, slot + 1, nextDelayMs);
            return;
        }
        if (session.launchedIds.size() >= session.request.total()) {
            session.state = RunState.RUNNING;
            session.lastProgressAtMs = currentTimeMs.getAsLong();
            scheduleMonitor(session);
            return;
        }
        int failureLimit = Math.max(5, Math.min(25, session.request.total()));
        if (session.failedStarts >= failureLimit) {
            session.state = RunState.FAILED;
            session.lastError = "Cohort start stopped after " + session.failedStarts + " failed Agent starts";
            notifyTerminal(session);
            return;
        }
        try {
            long waveWindowMs = session.request.intervalSeconds() * 1_000L;
            scheduleWave(session, Math.max(0L,
                    waveWindowMs - launchOffsetsMs[launchOffsetsMs.length - 1]));
        } catch (RuntimeException failure) {
            fail(session, failure);
        }
    }

    private void scheduleLaunch(Session session, long[] launchOffsetsMs, int slot, long delayMs) {
        synchronized (this) {
            if (session.state != RunState.RELEASING) {
                return;
            }
            try {
                session.scheduledWave = hooks.schedule(
                        () -> handoffLaunch(session, launchOffsetsMs, slot), delayMs);
            } catch (RuntimeException failure) {
                fail(session, failure);
            }
        }
    }

    private static long[] launchOffsetsMs(Session session, int count) {
        long windowMs = session.request.intervalSeconds() * 1_000L;
        long[] offsets = new long[count];
        SplittableRandom random = new SplittableRandom(
                session.runSeed ^ WAVE_JITTER_DOMAIN ^ Integer.toUnsignedLong(session.nextWave++));
        for (int slot = 0; slot < count; slot++) {
            long start = windowMs * slot / count;
            long end = windowMs * (slot + 1L) / count;
            offsets[slot] = start + random.nextLong(Math.max(1L, end - start));
        }
        return offsets;
    }

    private void stopOnWorker(Session session) {
        List<Integer> launched;
        synchronized (this) {
            launched = List.copyOf(session.launchedIds);
        }
        boolean allStopRequestsSucceeded = true;
        for (Integer characterId : launched) {
            try {
                hooks.stopAgent(characterId);
            } catch (RuntimeException failure) {
                allStopRequestsSucceeded = false;
                synchronized (this) {
                    session.lastError = message(failure);
                }
            }
        }
        if (!allStopRequestsSucceeded) {
            synchronized (this) {
                session.state = RunState.FAILED;
                notifyTerminal(session);
            }
            return;
        }
        boolean released = true;
        try {
            hooks.releaseSession(session.sessionId);
        } catch (Exception failure) {
            released = false;
            synchronized (this) {
                session.lastError = message(failure);
            }
        }
        synchronized (this) {
            session.state = released ? RunState.STOPPED : RunState.FAILED;
            cancelMonitor(session);
            notifyTerminal(session);
        }
    }

    private void scheduleWave(Session session, long delayMs) {
        session.scheduledWave = hooks.schedule(() -> handoffWave(session), delayMs);
    }

    private void cancelScheduled(Session session) {
        ScheduledFuture<?> scheduled = session.scheduledWave;
        session.scheduledWave = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    private void scheduleMonitor(Session session) {
        if (session.monitor != null || session.state != RunState.RUNNING) {
            return;
        }
        session.monitor = hooks.schedule(() -> monitor(session), MONITOR_INTERVAL_MS);
    }

    private void monitor(Session session) {
        synchronized (this) {
            session.monitor = null;
            status(session);
            if (session.state == RunState.RUNNING) {
                scheduleMonitor(session);
            }
        }
    }

    private void cancelMonitor(Session session) {
        ScheduledFuture<?> monitor = session.monitor;
        session.monitor = null;
        if (monitor != null) {
            monitor.cancel(false);
        }
    }

    private Status status(Session session) {
        int running = 0;
        int completed = 0;
        int failedRuns = 0;
        int missing = 0;
        for (Integer characterId : session.launchedIds) {
            switch (hooks.agentState(characterId)) {
                case RUNNING -> running++;
                case COMPLETED -> completed++;
                case FAILED -> failedRuns++;
                case MISSING -> missing++;
            }
        }
        if (session.state == RunState.RUNNING) {
            updateRunState(session, running, completed, failedRuns, missing);
        }
        return new Status(session.sessionId, session.state, session.request.total(), session.request.batch(),
                session.request.intervalSeconds(), session.runSeed, session.request.realismMode(),
                session.launchedIds.size(),
                session.failedStarts, running, completed, failedRuns, missing,
                Math.max(0, session.request.total() - session.launchedIds.size()),
                session.admissionDeferrals, session.lastError);
    }

    private void updateRunState(Session session, int running, int completed, int failedRuns, int missing) {
        long nowMs = currentTimeMs.getAsLong();
        int terminal = completed + failedRuns + missing;
        if (terminal > session.lastTerminalCount) {
            session.lastTerminalCount = terminal;
            session.lastProgressAtMs = nowMs;
            session.allTerminalSinceMs = -1L;
        }
        if (completed == session.request.total()) {
            session.state = RunState.COMPLETED;
            cancelMonitor(session);
            notifyTerminal(session);
            return;
        }
        if (running == 0 && terminal == session.launchedIds.size()) {
            if (session.allTerminalSinceMs < 0L) {
                session.allTerminalSinceMs = nowMs;
            }
            if (nowMs - session.allTerminalSinceMs >= TERMINAL_FAILURE_GRACE_MS) {
                session.state = RunState.COMPLETED_WITH_FAILURES;
                session.lastError = "Cohort ended with " + failedRuns + " failed and " + missing
                        + " missing Agent runs";
                cancelMonitor(session);
                notifyTerminal(session);
            }
            return;
        }
        session.allTerminalSinceMs = -1L;
        if (nowMs - session.lastProgressAtMs >= stallTimeoutMs) {
            session.state = RunState.STALLED;
            session.lastError = "No Agent reached a new terminal state for " + stallTimeoutMs + " ms";
            cancelMonitor(session);
            notifyTerminal(session);
        }
    }

    private void notifyTerminal(Session session) {
        if (session.terminalNotifiedState == session.state) {
            return;
        }
        session.terminalNotifiedState = session.state;
        hooks.runTerminated(session.sessionId, session.state);
    }

    private static int configuredMaxTotal() {
        int configured = YamlConfig.config.server.AGENT_MAPLE_ISLAND_COHORT_MAX_TOTAL;
        return configured <= 0 ? DEFAULT_MAX_TOTAL : configured;
    }

    private static long configuredStallTimeoutMs() {
        int configured = YamlConfig.config.server.AGENT_MAPLE_ISLAND_COHORT_STALL_TIMEOUT_MS;
        return configured <= 0 ? DEFAULT_STALL_TIMEOUT_MS : configured;
    }

    private Session requireSession(int world, int channel) {
        Session session = sessions.get(new Shard(world, channel));
        if (session == null) {
            throw new IllegalStateException("No Maple Island cohort exists on this channel");
        }
        return session;
    }

    private void fail(Session session, Throwable failure) {
        session.state = RunState.FAILED;
        session.lastError = message(failure);
        cancelScheduled(session);
        cancelMonitor(session);
        notifyTerminal(session);
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static final class Session {
        private final String sessionId;
        private final StartRequest request;
        private final long runSeed;
        private final Set<Integer> attemptedIds = new HashSet<>();
        private final Set<Integer> launchedIds = new LinkedHashSet<>();
        private RunState state = RunState.RELEASING;
        private ScheduledFuture<?> scheduledWave;
        private ScheduledFuture<?> monitor;
        private boolean waveInFlight;
        private int nextWave;
        private int nextOrdinal = 1;
        private int failedStarts;
        private int admissionDeferrals;
        private int lastTerminalCount;
        private long lastProgressAtMs;
        private long allTerminalSinceMs = -1L;
        private RunState terminalNotifiedState;
        private String lastError = "";

        private Session(String sessionId, StartRequest request, long runSeed, long nowMs) {
            this.sessionId = sessionId;
            this.request = request;
            this.runSeed = runSeed;
            this.lastProgressAtMs = nowMs;
        }
    }
}
