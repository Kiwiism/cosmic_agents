package server.agents.field;

import client.Character;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/** Exact server-timed 1->5->1 field-scaling benchmark harness. */
public final class AgentFieldLadderRuntime {
    public static final long WINDOW_MS = 120_000L;
    private static final Map<Integer, Run> runs = new ConcurrentHashMap<>();

    private AgentFieldLadderRuntime() {
    }

    public static synchronized StartResult start(
            Character operator, List<AgentRuntimeEntry> entries, long nowMs) {
        if (operator == null || operator.getMap() == null || entries == null || entries.size() != 5) {
            return new StartResult(false, "The ladder requires a live operator and exactly five Agents.", "");
        }
        Run previous = runs.get(operator.getId());
        if (previous != null && previous.active) {
            return new StartResult(false, "A field ladder is already active for this operator.", previous.runId);
        }
        List<AgentRuntimeEntry> ordered = entries.stream()
                .sorted(java.util.Comparator.comparingInt(AgentFieldLadderRuntime::jobOrder))
                .toList();
        if (ordered.stream().map(AgentFieldLadderRuntime::jobOrder).distinct().count() != 5) {
            return new StartResult(false,
                    "The ladder requires one Warrior, Bowman, Magician, Thief, and Pirate.", "");
        }
        AgentFieldRuntime.StartResult field = AgentFieldRuntime.start(
                operator, List.of(ordered.getFirst()), AgentFieldMode.PARTY,
                Set.of(), 1, true, nowMs);
        if (!field.success()) {
            return new StartResult(false, field.message(), "");
        }
        AgentMovementCommandRuntime.grind(ordered.getFirst());
        Run run = new Run("ladder-" + field.sessionId(), operator, ordered, nowMs);
        run.samples.add(sample(0, nowMs, operator));
        runs.put(operator.getId(), run);
        for (int boundary = 1; boundary <= 9; boundary++) {
            int index = boundary;
            run.tasks.add(AgentSchedulerRuntime.schedule(
                    () -> transition(operator.getId(), index), WINDOW_MS * boundary));
        }
        return new StartResult(true, "Started exact 1->5->1 field ladder.", run.runId);
    }

    private static int jobOrder(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        return agent == null ? Integer.MAX_VALUE : switch (agent.getJob().getId()) {
            case 100 -> 0;
            case 300 -> 1;
            case 200 -> 2;
            case 400 -> 3;
            case 500 -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    public static RunReport reportForMapId(int mapId) {
        return runs.values().stream()
                .filter(run -> run.mapId == mapId)
                .max(java.util.Comparator.comparingLong(run -> run.startedAtMs))
                .map(Run::report)
                .orElse(null);
    }

    private static void transition(int operatorId, int boundary) {
        Run run = runs.get(operatorId);
        if (run == null || !run.active) {
            return;
        }
        synchronized (run) {
            if (!run.active || run.nextBoundary != boundary) {
                return;
            }
            long nowMs = System.currentTimeMillis();
            run.samples.add(sample(boundary, nowMs, run.operator));
            run.nextBoundary++;
            if (boundary <= 4) {
                AgentRuntimeEntry entry = run.entries.get(boundary);
                if (!AgentFieldRuntime.add(run.operator, entry,
                        AgentFieldIntent.freeGrind(run.fieldSessionId()), nowMs)) {
                    run.fail("could not add " + AgentRuntimeIdentityRuntime.botName(entry), nowMs);
                    return;
                }
                AgentMovementCommandRuntime.grind(entry);
                return;
            }
            if (boundary <= 8) {
                AgentRuntimeEntry entry = run.entries.get(boundary - 5);
                Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                if (agent == null || !AgentFieldRuntime.remove(run.operator, agent.getId(), nowMs)) {
                    run.fail("could not remove " + AgentRuntimeIdentityRuntime.botName(entry), nowMs);
                    return;
                }
                AgentMovementCommandRuntime.stop(entry);
                return;
            }
            AgentFieldRuntime.stop(run.operator, nowMs);
            run.entries.forEach(AgentMovementCommandRuntime::stop);
            run.active = false;
            run.completedAtMs = nowMs;
        }
    }

    private static BoundarySample sample(int boundary, long nowMs, Character operator) {
        return new BoundarySample(boundary, nowMs, AgentFieldRuntime.snapshot(operator, nowMs));
    }

    public record StartResult(boolean success, String message, String runId) {
    }

    public record BoundarySample(int boundary, long observedAtMs, AgentFieldSnapshot field) {
    }

    public record RunReport(
            String runId,
            int mapId,
            long windowMs,
            long startedAtMs,
            long completedAtMs,
            boolean active,
            int nextBoundary,
            String failure,
            List<String> agents,
            List<BoundarySample> samples) {
        public RunReport {
            agents = List.copyOf(agents);
            samples = List.copyOf(samples);
        }
    }

    private static final class Run {
        private final String runId;
        private final Character operator;
        private final List<AgentRuntimeEntry> entries;
        private final int mapId;
        private final long startedAtMs;
        private final ArrayList<ScheduledFuture<?>> tasks = new ArrayList<>();
        private final ArrayList<BoundarySample> samples = new ArrayList<>();
        private volatile boolean active = true;
        private volatile int nextBoundary = 1;
        private volatile long completedAtMs;
        private volatile String failure = "";

        private Run(String runId, Character operator, List<AgentRuntimeEntry> entries, long startedAtMs) {
            this.runId = runId;
            this.operator = operator;
            this.entries = List.copyOf(entries);
            this.mapId = operator.getMapId();
            this.startedAtMs = startedAtMs;
        }

        private String fieldSessionId() {
            AgentFieldSnapshot snapshot = AgentFieldRuntime.snapshot(operator, System.currentTimeMillis());
            return snapshot == null ? "" : snapshot.sessionId();
        }

        private void fail(String reason, long nowMs) {
            failure = reason;
            active = false;
            completedAtMs = nowMs;
            AgentFieldRuntime.stop(operator, nowMs);
            entries.forEach(AgentMovementCommandRuntime::stop);
            tasks.forEach(task -> task.cancel(false));
        }

        private synchronized RunReport report() {
            return new RunReport(runId, mapId, WINDOW_MS, startedAtMs, completedAtMs,
                    active, nextBoundary, failure,
                    entries.stream().map(AgentRuntimeIdentityRuntime::botName).toList(),
                    List.copyOf(samples));
        }
    }
}
