package server.agents.population;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministically converges the explicit roster toward its bounded target. */
public final class AgentPopulationReconciler {
    public record Result(boolean enabled, int target, int liveBefore, int liveAfter,
                         int started, int stopped, int failed) {
    }

    private final AgentPopulationRegistry registry;
    private final AgentPopulationCurve curve;
    private final AgentPopulationPolicy policy;
    private final AgentPopulationSessionService sessions;
    private final AgentPopulationMetrics metrics;
    private final Clock clock;

    public AgentPopulationReconciler(AgentPopulationRegistry registry,
                                     AgentPopulationCurve curve,
                                     AgentPopulationPolicy policy,
                                     AgentPopulationSessionService sessions,
                                     AgentPopulationMetrics metrics) {
        this(registry, curve, policy, sessions, metrics, Clock.systemUTC());
    }

    AgentPopulationReconciler(AgentPopulationRegistry registry,
                              AgentPopulationCurve curve,
                              AgentPopulationPolicy policy,
                              AgentPopulationSessionService sessions,
                              AgentPopulationMetrics metrics,
                              Clock clock) {
        this.registry = registry;
        this.curve = curve;
        this.policy = policy;
        this.sessions = sessions;
        this.metrics = metrics;
        this.clock = clock;
    }

    public Result reconcile() {
        long startedAt = clock.millis();
        AgentPopulationSnapshot snapshot = registry.snapshot();
        List<AgentPopulationRecord> records = snapshot.agents();
        int target = snapshot.enabled() ? curve.target(records.size(), snapshot.multiplier()) : 0;
        List<AgentPopulationRecord> live = new ArrayList<>();
        List<AgentPopulationRecord> offline = new ArrayList<>();
        for (AgentPopulationRecord record : records) {
            (sessions.isLive(record.characterId()) ? live : offline).add(record);
        }
        int liveBefore = live.size();
        if (!snapshot.enabled()) {
            metrics.recordCensus(0, liveBefore, records.size());
            metrics.recordSweep(startedAt, Math.max(0L, clock.millis() - startedAt));
            return new Result(false, 0, liveBefore, liveBefore, 0, 0, 0);
        }
        int started = 0;
        int stopped = 0;
        int failed = 0;
        int budget = policy.maxActionsPerSweep();

        if (live.size() < target) {
            for (AgentPopulationRecord record : offline) {
                if (started >= budget || live.size() + started >= target) {
                    break;
                }
                try {
                    if (sessions.start(record)) {
                        started++;
                    }
                } catch (Exception failure) {
                    failed++;
                    metrics.recordFailure();
                }
            }
        } else if (live.size() > target) {
            Collections.reverse(live);
            for (AgentPopulationRecord record : live) {
                if (stopped >= budget || liveBefore - stopped <= target) {
                    break;
                }
                try {
                    if (sessions.stop(record)) {
                        stopped++;
                    }
                } catch (Exception failure) {
                    failed++;
                    metrics.recordFailure();
                }
            }
        }

        int liveAfter = liveBefore + started - stopped;
        metrics.recordCensus(target, liveAfter, records.size());
        metrics.recordSweep(startedAt, Math.max(0L, clock.millis() - startedAt));
        return new Result(snapshot.enabled(), target, liveBefore, liveAfter, started, stopped, failed);
    }
}
