package server.agents.plans.mapleisland.cohort;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Serialized wave scheduler. Timer callbacks only hand work to the external cohort worker. */
public final class MapleIslandCohortRunService {
    public static final int MAX_TOTAL = 100;
    public static final int MAX_BATCH = 10;
    public static final int MIN_INTERVAL_SECONDS = 5;
    public static final int MAX_INTERVAL_SECONDS = 3_600;

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
            if (total < 1 || total > MAX_TOTAL) {
                throw new IllegalArgumentException("total must be between 1 and " + MAX_TOTAL);
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
    }

    private final Hooks hooks;
    private final Map<Shard, Session> sessions = new ConcurrentHashMap<>();

    public MapleIslandCohortRunService(Hooks hooks) {
        this.hooks = hooks;
    }

    public synchronized Status start(StartRequest request) {
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
        Session session = new Session(sessionId, request, seed);
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
        try {
            hooks.dispatch(() -> stopOnWorker(session));
        } catch (RuntimeException failure) {
            session.state = RunState.FAILED;
            session.lastError = message(failure);
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
            session.waveInFlight = true;
        }
        try {
            hooks.dispatch(() -> executeWave(session));
        } catch (RuntimeException failure) {
            synchronized (this) {
                session.waveInFlight = false;
                fail(session, failure);
            }
        }
    }

    private void executeWave(Session session) {
        int count;
        Set<Integer> excluded;
        synchronized (this) {
            if (session.state != RunState.RELEASING) {
                session.waveInFlight = false;
                return;
            }
            count = Math.min(session.request.batch(), session.request.total() - session.launchedIds.size());
            excluded = Set.copyOf(session.attemptedIds);
        }

        try {
            List<Agent> agents = hooks.acquire(count, session.sessionId,
                    session.request.ownerCharacterId(), session.request.world(), session.request.channel(), excluded);
            if (agents.isEmpty()) {
                throw new IllegalStateException("No reusable cohort Agent could be leased");
            }
            for (Agent agent : agents) {
                AgentContext context;
                boolean releaseCancelledWave;
                synchronized (this) {
                    session.attemptedIds.add(agent.characterId());
                    releaseCancelledWave = session.state != RunState.RELEASING;
                    context = releaseCancelledWave ? null : new AgentContext(
                            session.sessionId, session.runSeed, session.nextOrdinal++,
                            session.request.ownerCharacterId(), session.request.world(), session.request.channel(),
                            session.request.realismMode());
                }
                if (releaseCancelledWave) {
                    hooks.releaseSession(session.sessionId);
                    break;
                }
                try {
                    hooks.startAgent(agent, context);
                    synchronized (this) {
                        session.launchedIds.add(agent.characterId());
                    }
                } catch (Exception failure) {
                    synchronized (this) {
                        session.failedStarts++;
                        session.lastError = message(failure);
                    }
                    try {
                        hooks.markBroken(agent, session.sessionId, message(failure));
                    } catch (Exception markFailure) {
                        synchronized (this) {
                            session.lastError = "Failed to persist broken lease after "
                                    + message(failure) + "; " + message(markFailure);
                            session.state = RunState.FAILED;
                        }
                        break;
                    }
                }
            }
        } catch (Exception failure) {
            synchronized (this) {
                session.failedStarts++;
                session.lastError = message(failure);
            }
        } finally {
            synchronized (this) {
                session.waveInFlight = false;
                finishWave(session);
            }
        }
    }

    private void finishWave(Session session) {
        if (session.state != RunState.RELEASING) {
            return;
        }
        if (session.launchedIds.size() >= session.request.total()) {
            session.state = RunState.RUNNING;
            return;
        }
        int failureLimit = Math.max(5, Math.min(25, session.request.total()));
        if (session.failedStarts >= failureLimit) {
            session.state = RunState.FAILED;
            session.lastError = "Cohort start stopped after " + session.failedStarts + " failed Agent starts";
            return;
        }
        try {
            scheduleWave(session, session.request.intervalSeconds() * 1_000L);
        } catch (RuntimeException failure) {
            fail(session, failure);
        }
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
        if (session.state == RunState.RUNNING && completed == session.request.total()) {
            session.state = RunState.COMPLETED;
        }
        return new Status(session.sessionId, session.state, session.request.total(), session.request.batch(),
                session.request.intervalSeconds(), session.runSeed, session.request.realismMode(),
                session.launchedIds.size(),
                session.failedStarts, running, completed, failedRuns, missing,
                Math.max(0, session.request.total() - session.launchedIds.size()), session.lastError);
    }

    private Session requireSession(int world, int channel) {
        Session session = sessions.get(new Shard(world, channel));
        if (session == null) {
            throw new IllegalStateException("No Maple Island cohort exists on this channel");
        }
        return session;
    }

    private static void fail(Session session, Throwable failure) {
        session.state = RunState.FAILED;
        session.lastError = message(failure);
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
        private boolean waveInFlight;
        private int nextOrdinal = 1;
        private int failedStarts;
        private String lastError = "";

        private Session(String sessionId, StartRequest request, long runSeed) {
            this.sessionId = sessionId;
            this.request = request;
            this.runSeed = runSeed;
        }
    }
}
