package server.agents.economy.activity;

import client.Character;
import server.agents.events.AgentEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.resources.events.AgentItemQuantityChangedEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Opt-in observer of real agent sessions. It never changes gameplay or awards holdings. */
public final class LiveActivityCalibrationRuntime {
    private static final Map<Integer, Session> ACTIVE = new ConcurrentHashMap<>();

    private LiveActivityCalibrationRuntime() { }

    public static void begin(Character agent, String agentBuild, long nowMs) {
        if (agent == null || agent.getId() <= 0 || agent.getMapId() <= 0)
            throw new IllegalArgumentException("live agent session is required");
        Session session = new Session(UUID.randomUUID().toString(), agent.getId(), agentBuild,
                agent.getMapId(), agent.getLevel(), EconomyJobFamily.of(agent), nowMs);
        if (ACTIVE.putIfAbsent(agent.getId(), session) != null)
            throw new IllegalStateException("agent calibration session already active");
    }

    public static ActivityCalibrationSample end(Character agent, boolean died, long nowMs,
                                                ActivityCalibrationSink sink) {
        Session session = agent == null ? null : ACTIVE.get(agent.getId());
        if (session == null) throw new IllegalStateException("agent calibration session is not active");
        ActivityCalibrationSample sample = session.finish(died, nowMs, sink);
        if (!ACTIVE.remove(agent.getId(), session))
            throw new IllegalStateException("agent calibration session changed while saving");
        return sample;
    }

    public static void observe(AgentEvent event) {
        int agentId = event instanceof AgentMobKilledEvent killed ? killed.agentId()
                : event instanceof AgentItemQuantityChangedEvent changed ? changed.agentId() : 0;
        Session session = ACTIVE.get(agentId);
        if (session != null) session.observe(event);
    }

    public static Status status(Character agent) {
        Session session = agent == null ? null : ACTIVE.get(agent.getId());
        return session == null ? null : new Status(session.build, session.mapId, session.level,
                session.job, Instant.ofEpochMilli(session.startedAt));
    }

    public record Status(String agentBuild, int mapId, int level,
                         String jobFamily, Instant startedAt) { }

    private static final class Session {
        private final String sampleId;
        private final int agentId;
        private final String build;
        private final int mapId;
        private final int level;
        private final String job;
        private final long startedAt;
        private final Map<Integer, Integer> kills = new HashMap<>();
        private final Map<Integer, Integer> consumed = new HashMap<>();
        private boolean closed;

        private Session(String sampleId, int agentId, String build, int mapId, int level,
                        String job, long startedAt) {
            this.sampleId = sampleId;
            this.agentId = agentId;
            this.build = build;
            this.mapId = mapId;
            this.level = level;
            this.job = job;
            this.startedAt = startedAt;
        }

        private synchronized void observe(AgentEvent event) {
            if (closed) return;
            if (event instanceof AgentMobKilledEvent killed && killed.mapId() == mapId) {
                kills.merge(killed.mobId(), 1, Math::addExact);
            } else if (event instanceof AgentItemQuantityChangedEvent changed
                    && "consume".equals(changed.source()) && changed.previousQuantity() > changed.quantity()) {
                consumed.merge(changed.itemId(), changed.previousQuantity() - changed.quantity(), Math::addExact);
            }
        }

        private synchronized ActivityCalibrationSample finish(boolean died, long completedAt,
                                                              ActivityCalibrationSink sink) {
            if (closed) throw new IllegalStateException("agent calibration session is already closed");
            ActivityCalibrationSample sample = new ActivityCalibrationSample(sampleId, agentId, build, mapId, level, job,
                    Instant.ofEpochMilli(startedAt), Instant.ofEpochMilli(completedAt),
                    kills, consumed, died);
            sink.append(sample);
            closed = true;
            return sample;
        }
    }
}
