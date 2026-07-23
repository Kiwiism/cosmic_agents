package server.agents.plans.mapleisland.cohort;

import client.Character;
import config.YamlConfig;
import server.agents.plans.amherst.AmherstQuestCatalog;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.capabilities.runtime.AgentCapabilityJournalEventType;
import server.agents.diagnostics.AgentRunObservationRuntime;
import server.agents.plans.amherst.AmherstPlanObservation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Event-driven cohort measurements. No polling or per-tick position sampling is performed. */
public final class MapleIslandCohortTelemetryService {
    public record DurationSummary(int samples,
                                  long averageMs,
                                  long medianMs,
                                  long p95Ms,
                                  long fastestMs,
                                  String fastestAgent,
                                  long slowestMs,
                                  String slowestAgent) {
    }

    public record ObjectiveSummary(String objectiveId,
                                   int samples,
                                   long averageMs,
                                   long slowestMs,
                                   String slowestAgent) {
    }

    public record ActiveObjective(String agentName, String objectiveId, long elapsedMs) {
    }

    public record Snapshot(String sessionId,
                           MapleIslandCohortRealismMode realismMode,
                           int trackedAgents,
                           DurationSummary amherst,
                           DurationSummary southperry,
                           DurationSummary completion,
                           int retries,
                           int timeouts,
                           int blocks,
                           int failures,
                           int liveStateRecoveries,
                           int movementUnstucks,
                           ActiveObjective longestActiveObjective,
                           List<ObjectiveSummary> slowestObjectives) {
    }

    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Map<Integer, AgentTracker> agents = new LinkedHashMap<>();
    private final int retainedRuns;

    public MapleIslandCohortTelemetryService() {
        this(configuredRetainedRuns());
    }

    MapleIslandCohortTelemetryService(int retainedRuns) {
        this.retainedRuns = Math.max(1, retainedRuns);
    }

    public synchronized void beginSession(String sessionId, MapleIslandCohortRealismMode realismMode) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.computeIfAbsent(sessionId, ignored -> new Session(sessionId, realismMode));
        }
    }

    public synchronized void register(String sessionId,
                                      MapleIslandCohortRealismMode realismMode,
                                      Character agent,
                                      long spawnedAtMs) {
        if (sessionId == null || sessionId.isBlank() || agent == null) {
            return;
        }
        beginSession(sessionId, realismMode);
        Session session = sessions.get(sessionId);
        AgentTracker tracker = new AgentTracker(session, agent.getId(), agent.getName(), spawnedAtMs);
        AgentTracker replaced = agents.put(agent.getId(), tracker);
        if (replaced != null) {
            AgentRunObservationRuntime.unregister(agent.getId(), replaced.listener);
            replaced.session.agents.remove(agent.getId());
        }
        session.agents.put(agent.getId(), tracker);
        tracker.listener = new AgentRunObservationRuntime.Listener() {
            @Override
            public void onMapChanged(Character observed, int mapId, long nowMs) {
                recordMapChanged(observed.getId(), mapId, nowMs);
            }

            @Override
            public void onRecovery(Character observed, String recoveryType, long nowMs) {
                recordRecovery(observed.getId(), recoveryType);
            }
        };
        AgentRunObservationRuntime.register(agent.getId(), tracker.listener);
        recordMapChanged(agent.getId(), agent.getMapId(), spawnedAtMs);
    }

    public synchronized void observe(int agentId, AmherstPlanObservation observation) {
        AgentTracker tracker = agents.get(agentId);
        if (tracker == null || observation == null) {
            return;
        }
        switch (observation.type()) {
            case OBJECTIVE_STARTED -> {
                tracker.currentObjectiveId = observation.objectiveId();
                tracker.currentObjectiveStartedAtMs = observation.timestampMs();
            }
            case OBJECTIVE_FINISHED -> finishObjective(tracker, observation);
            case CAPABILITY_EVENT -> recordCapabilityEvent(tracker.session, observation);
            case PLAN_COMPLETED -> {
                tracker.completedAtMs = first(tracker.completedAtMs, observation.timestampMs());
                detach(tracker);
            }
            case PLAN_ERROR -> {
                tracker.session.planErrors++;
                detach(tracker);
            }
            default -> {
            }
        }
    }

    public synchronized void startupFailed(String sessionId, String agentName) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.startupFailures++;
        }
    }

    public synchronized void detach(int agentId) {
        AgentTracker tracker = agents.get(agentId);
        if (tracker != null) {
            detach(tracker);
        }
    }

    public synchronized Snapshot snapshot(String sessionId, long nowMs) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (session.finalSnapshot != null) {
            return session.finalSnapshot;
        }
        List<AgentTracker> trackers = new ArrayList<>(session.agents.values());
        ActiveObjective longest = trackers.stream()
                .filter(tracker -> tracker.currentObjectiveStartedAtMs > 0L)
                .map(tracker -> new ActiveObjective(tracker.agentName, tracker.currentObjectiveId,
                        Math.max(0L, nowMs - tracker.currentObjectiveStartedAtMs)))
                .max(Comparator.comparingLong(ActiveObjective::elapsedMs))
                .orElse(null);
        List<ObjectiveSummary> objectives = session.objectives.entrySet().stream()
                .map(entry -> entry.getValue().summary(entry.getKey()))
                .sorted(Comparator.comparingLong(ObjectiveSummary::averageMs).reversed())
                .limit(3)
                .toList();
        return new Snapshot(session.sessionId, session.realismMode, trackers.size(),
                summarize(trackers, Milestone.AMHERST),
                summarize(trackers, Milestone.SOUTHPERRY),
                summarize(trackers, Milestone.COMPLETION),
                session.retries, session.timeouts, session.blocks,
                session.capabilityFailures + session.planErrors + session.startupFailures,
                session.liveStateRecoveries, session.movementUnstucks, longest, objectives);
    }

    public synchronized boolean markFinalSummaryLogged(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null || session.finalSummaryLogged) {
            return false;
        }
        session.finalSummaryLogged = true;
        return true;
    }

    public synchronized Snapshot completeSession(String sessionId, long nowMs) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (session.finalSnapshot == null) {
            session.finalSnapshot = snapshot(sessionId, nowMs);
            for (AgentTracker tracker : List.copyOf(session.agents.values())) {
                detach(tracker);
                agents.remove(tracker.agentId, tracker);
            }
            session.agents.clear();
            session.objectives.clear();
            pruneCompletedSessions();
        }
        return session.finalSnapshot;
    }

    private void pruneCompletedSessions() {
        int completed = (int) sessions.values().stream()
                .filter(session -> session.finalSnapshot != null)
                .count();
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext() && completed > retainedRuns) {
            Session session = iterator.next().getValue();
            if (session.finalSnapshot != null) {
                iterator.remove();
                completed--;
            }
        }
    }

    private static int configuredRetainedRuns() {
        int configured = config.AgentYamlConfig.config.agent.AGENT_MAPLE_ISLAND_COHORT_TELEMETRY_RETAINED_RUNS;
        return configured <= 0 ? 20 : configured;
    }

    synchronized void recordMapChanged(int agentId, int mapId, long nowMs) {
        AgentTracker tracker = agents.get(agentId);
        if (tracker == null) {
            return;
        }
        if (mapId == AmherstQuestCatalog.FINAL_MAP_ID) {
            tracker.amherstAtMs = first(tracker.amherstAtMs, nowMs);
        }
        if (mapId == MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            tracker.southperryAtMs = first(tracker.southperryAtMs, nowMs);
        }
    }

    private void recordRecovery(int agentId, String recoveryType) {
        AgentTracker tracker = agents.get(agentId);
        if (tracker != null && "movement-unstuck".equals(recoveryType)) {
            tracker.session.movementUnstucks++;
        }
    }

    private static void recordCapabilityEvent(Session session, AmherstPlanObservation observation) {
        if (observation.capabilityEvent() == null) {
            return;
        }
        AgentCapabilityJournalEventType type = observation.capabilityEvent().type();
        switch (type) {
            case RETRY -> session.retries++;
            case TIMED_OUT -> session.timeouts++;
            case BLOCKED -> session.blocks++;
            case FAILED -> session.capabilityFailures++;
            default -> {
            }
        }
    }

    private static void finishObjective(AgentTracker tracker, AmherstPlanObservation observation) {
        long startedAtMs = tracker.currentObjectiveStartedAtMs;
        if (startedAtMs > 0L && observation.timestampMs() >= startedAtMs) {
            tracker.session.objectives
                    .computeIfAbsent(observation.objectiveId(), ignored -> new Aggregate())
                    .add(observation.timestampMs() - startedAtMs, tracker.agentName);
        }
        if (observation.message().contains("live state")) {
            tracker.session.liveStateRecoveries++;
        }
        tracker.currentObjectiveId = "";
        tracker.currentObjectiveStartedAtMs = 0L;
    }

    private static DurationSummary summarize(List<AgentTracker> trackers, Milestone milestone) {
        List<Sample> samples = trackers.stream()
                .map(tracker -> new Sample(tracker.agentName, milestone.elapsed(tracker)))
                .filter(sample -> sample.elapsedMs >= 0L)
                .toList();
        if (samples.isEmpty()) {
            return new DurationSummary(0, 0L, 0L, 0L, 0L, "", 0L, "");
        }
        List<Sample> ordered = samples.stream()
                .sorted(Comparator.comparingLong(Sample::elapsedMs))
                .toList();
        Sample fastest = samples.stream().min(Comparator.comparingLong(Sample::elapsedMs)).orElseThrow();
        Sample slowest = samples.stream().max(Comparator.comparingLong(Sample::elapsedMs)).orElseThrow();
        long total = samples.stream().mapToLong(Sample::elapsedMs).sum();
        return new DurationSummary(samples.size(), total / samples.size(),
                percentile(ordered, 0.50d), percentile(ordered, 0.95d),
                fastest.elapsedMs, fastest.agentName, slowest.elapsedMs, slowest.agentName);
    }

    private static long percentile(List<Sample> ordered, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * ordered.size()) - 1);
        return ordered.get(index).elapsedMs;
    }

    private static long first(long current, long candidate) {
        return current == 0L ? candidate : current;
    }

    private static void detach(AgentTracker tracker) {
        if (tracker.listener != null) {
            AgentRunObservationRuntime.unregister(tracker.agentId, tracker.listener);
            tracker.listener = null;
        }
    }

    private enum Milestone {
        AMHERST {
            @Override long timestamp(AgentTracker tracker) { return tracker.amherstAtMs; }
        },
        SOUTHPERRY {
            @Override long timestamp(AgentTracker tracker) { return tracker.southperryAtMs; }
        },
        COMPLETION {
            @Override long timestamp(AgentTracker tracker) { return tracker.completedAtMs; }
        };

        abstract long timestamp(AgentTracker tracker);

        long elapsed(AgentTracker tracker) {
            long timestamp = timestamp(tracker);
            return timestamp == 0L ? -1L : Math.max(0L, timestamp - tracker.spawnedAtMs);
        }
    }

    private record Sample(String agentName, long elapsedMs) {
    }

    private static final class Session {
        private final String sessionId;
        private final MapleIslandCohortRealismMode realismMode;
        private final Map<Integer, AgentTracker> agents = new LinkedHashMap<>();
        private final Map<String, Aggregate> objectives = new LinkedHashMap<>();
        private int retries;
        private int timeouts;
        private int blocks;
        private int capabilityFailures;
        private int planErrors;
        private int startupFailures;
        private int liveStateRecoveries;
        private int movementUnstucks;
        private boolean finalSummaryLogged;
        private Snapshot finalSnapshot;

        private Session(String sessionId, MapleIslandCohortRealismMode realismMode) {
            this.sessionId = sessionId;
            this.realismMode = realismMode;
        }
    }

    private static final class AgentTracker {
        private final Session session;
        private final int agentId;
        private final String agentName;
        private final long spawnedAtMs;
        private AgentRunObservationRuntime.Listener listener;
        private long amherstAtMs;
        private long southperryAtMs;
        private long completedAtMs;
        private String currentObjectiveId = "";
        private long currentObjectiveStartedAtMs;

        private AgentTracker(Session session, int agentId, String agentName, long spawnedAtMs) {
            this.session = session;
            this.agentId = agentId;
            this.agentName = agentName;
            this.spawnedAtMs = spawnedAtMs;
        }
    }

    private static final class Aggregate {
        private int samples;
        private long totalMs;
        private long slowestMs;
        private String slowestAgent = "";

        private void add(long elapsedMs, String agentName) {
            samples++;
            totalMs += elapsedMs;
            if (elapsedMs >= slowestMs) {
                slowestMs = elapsedMs;
                slowestAgent = agentName;
            }
        }

        private ObjectiveSummary summary(String objectiveId) {
            return new ObjectiveSummary(objectiveId, samples,
                    samples == 0 ? 0L : totalMs / samples, slowestMs, slowestAgent);
        }
    }
}
