package server.agents.monitoring;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentTickFailureStateRuntime;
import server.agents.runtime.AgentTickSliceKind;
import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;
import server.agents.runtime.scheduler.AgentSessionId;
import server.agents.runtime.scheduler.AgentShardedTickScheduler;
import server.agents.runtime.scheduler.AgentTickScheduler;
import server.agents.runtime.scheduler.AgentWorkClass;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Bounded detail views for live scheduler diagnosis without retaining tick history. */
final class AgentSchedulerDetailDiagnostics {
    private static final int MAX_TOP = 10;

    record AgentView(int agentId,
                     long generation,
                     String name,
                     int mapId,
                     int mailboxDepth,
                     int recentFailures,
                     AgentSchedulerMode scheduleMode) {
        AgentView {
            name = name == null || name.isBlank() ? "<unknown>" : name;
            mailboxDepth = Math.max(0, mailboxDepth);
            recentFailures = Math.max(0, recentFailures);
        }

        AgentSessionId sessionId() {
            return new AgentSessionId(agentId, generation);
        }
    }

    private record MapView(int mapId,
                           int agents,
                           int mailboxDepth,
                           int recentFailures,
                           long estimatedCostNs,
                           long overdueMs) {
    }

    private static final class MutableMapView {
        private int agents;
        private int mailboxDepth;
        private int recentFailures;
        private long estimatedCostNs;
        private long overdueMs;
    }

    private AgentSchedulerDetailDiagnostics() {
    }

    static List<String> lines(String[] params) {
        AgentSchedulerMode mode = AgentSchedulerConfig.fromSystemProperties().mode();
        long nowMs = System.currentTimeMillis();
        List<AgentView> agents = captureAgents();
        List<AgentSchedulerRegistrationSnapshot> registrations = captureRegistrations(mode);
        String verb = params[0].toLowerCase(Locale.ROOT);
        return switch (verb) {
            case "top" -> top(params, agents, registrations, AgentPerformanceMonitor.snapshot(), nowMs);
            case "agent" -> agent(params, agents, registrations, nowMs);
            case "map" -> map(params, agents, registrations, nowMs);
            default -> usage();
        };
    }

    static List<String> stateLines(AgentSchedulerMode mode, int activeAgents, long nowMs) {
        List<AgentSchedulerRegistrationSnapshot> registrations = captureRegistrations(mode);
        if (mode == AgentSchedulerMode.LEGACY_PER_AGENT) {
            return List.of("Scheduler registrations: mode=LEGACY_PER_AGENT active=" + Math.max(0, activeAgents)
                    + " central-state=unavailable");
        }
        int ready = 0;
        int paused = 0;
        int quiescent = 0;
        int overdue = 0;
        Map<AgentPriorityClass, Integer> readyByPriority = new EnumMap<>(AgentPriorityClass.class);
        for (AgentSchedulerRegistrationSnapshot registration : registrations) {
            if (registration.quiescent()) {
                quiescent++;
            } else if (registration.paused()) {
                paused++;
            } else if (registration.ready()) {
                ready++;
                readyByPriority.merge(registration.priority(), 1, Integer::sum);
            }
            if (registration.overdueMs(nowMs) > 0L) {
                overdue++;
            }
        }
        int waiting = Math.max(0, registrations.size() - ready - paused - quiescent);
        return List.of(
                "Scheduler registrations: registered=" + registrations.size() + " ready=" + ready
                        + " waiting=" + waiting + " paused=" + paused + " quiescent=" + quiescent
                        + " overdue=" + overdue,
                "Scheduler ready priority: " + readyPrioritySummary(readyByPriority));
    }

    static List<String> costLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Scheduler work-class p99 cost (us):");
        java.util.Arrays.stream(AgentWorkClass.values())
                .map(workClass -> Map.entry(workClass, AgentSchedulerMetrics.workClassSnapshot(workClass)))
                .filter(entry -> entry.getValue().sampleCount() > 0)
                .sorted(Map.Entry.<AgentWorkClass, AgentSchedulerMetrics.WorkClassSnapshot>comparingByValue(
                        Comparator.comparingLong(AgentSchedulerMetrics.WorkClassSnapshot::durationP99Ns)).reversed())
                .limit(MAX_TOP)
                .forEach(entry -> lines.add("  work=" + entry.getKey() + " p50/p95/p99="
                        + micros(entry.getValue().durationP50Ns()) + "/"
                        + micros(entry.getValue().durationP95Ns()) + "/"
                        + micros(entry.getValue().durationP99Ns()) + " n=" + entry.getValue().sampleCount()));
        if (lines.size() == 1) {
            lines.add("  no work samples");
        }
        lines.add("Scheduler simulation-mode p99 cost (us):");
        for (AgentSimulationMode mode : AgentSimulationMode.values()) {
            AgentSchedulerMetrics.SimulationModeSnapshot snapshot = AgentSchedulerMetrics.simulationModeSnapshot(mode);
            if (snapshot.sampleCount() > 0) {
                lines.add("  mode=" + mode + " p50/p95/p99=" + micros(snapshot.durationP50Ns()) + "/"
                        + micros(snapshot.durationP95Ns()) + "/" + micros(snapshot.durationP99Ns())
                        + " n=" + snapshot.sampleCount());
            }
        }
        lines.add("Scheduler tick-slice p99 cost (us):");
        for (AgentTickSliceKind slice : AgentTickSliceKind.values()) {
            AgentSchedulerMetrics.TickSliceSnapshot snapshot = AgentSchedulerMetrics.tickSliceSnapshot(slice);
            if (snapshot.sampleCount() > 0) {
                lines.add("  slice=" + slice + " p50/p95/p99=" + micros(snapshot.durationP50Ns()) + "/"
                        + micros(snapshot.durationP95Ns()) + "/" + micros(snapshot.durationP99Ns())
                        + " n=" + snapshot.sampleCount());
            }
        }
        return List.copyOf(lines);
    }

    static List<String> top(String[] params,
                            List<AgentView> agents,
                            List<AgentSchedulerRegistrationSnapshot> registrations,
                            List<AgentPerformanceMonitor.SectionSnapshot> sections,
                            long nowMs) {
        if (params.length < 2) {
            return usage();
        }
        return switch (params[1].toLowerCase(Locale.ROOT)) {
            case "slow" -> topSlow(agents, registrations);
            case "overdue" -> topOverdue(agents, registrations, nowMs);
            case "map", "maps" -> topMaps(agents, registrations, nowMs);
            case "capability", "capabilities" -> topCapabilities(sections);
            case "mailbox", "mailboxes" -> topMailboxes(agents);
            case "failure", "failures", "failing" -> topFailures(agents);
            default -> usage();
        };
    }

    private static List<AgentView> captureAgents() {
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentSchedulerDetailDiagnostics::agentView)
                .sorted(Comparator.comparingInt(AgentView::agentId))
                .toList();
    }

    private static AgentView agentView(AgentRuntimeEntry entry) {
        ScheduledFuture<?> task = entry.scheduledTaskState().task();
        AgentSchedulerMode scheduleMode = task instanceof AgentScheduleHandle handle ? handle.mode() : null;
        return new AgentView(
                AgentRuntimeIdentityRuntime.botId(entry),
                entry.sessionGeneration(),
                AgentRuntimeIdentityRuntime.botName(entry),
                AgentRuntimeIdentityRuntime.botMapId(entry),
                entry.actionMailbox().size(),
                AgentTickFailureStateRuntime.failureCount(entry),
                scheduleMode);
    }

    static List<AgentSchedulerRegistrationSnapshot> captureRegistrations(AgentSchedulerMode mode) {
        return switch (mode) {
            case LEGACY_PER_AGENT -> List.of();
            case CENTRAL_SEQUENTIAL -> AgentTickScheduler.instance().registrationSnapshots();
            case CENTRAL_SHARDED -> AgentShardedTickScheduler.instance().registrationSnapshots();
        };
    }

    private static List<String> topSlow(
            List<AgentView> agents,
            List<AgentSchedulerRegistrationSnapshot> registrations) {
        if (registrations.isEmpty()) {
            return List.of("Slow Agent detail is unavailable without live central registrations.");
        }
        Map<AgentSessionId, AgentView> bySession = agentsBySession(agents);
        List<String> lines = new ArrayList<>();
        lines.add("Slowest Agents by scheduler cost EWMA:");
        registrations.stream()
                .sorted(Comparator.comparingLong(AgentSchedulerRegistrationSnapshot::estimatedCostNs)
                        .reversed()
                        .thenComparing(snapshot -> snapshot.sessionId().agentCharacterId()))
                .limit(MAX_TOP)
                .forEach(snapshot -> lines.add("  " + agentLabel(bySession.get(snapshot.sessionId()), snapshot.sessionId())
                        + " costUs=" + TimeUnit.NANOSECONDS.toMicros(snapshot.estimatedCostNs())
                        + " work=" + snapshot.workClass() + " mode=" + snapshot.simulationMode()));
        return List.copyOf(lines);
    }

    private static List<String> topOverdue(
            List<AgentView> agents,
            List<AgentSchedulerRegistrationSnapshot> registrations,
            long nowMs) {
        Map<AgentSessionId, AgentView> bySession = agentsBySession(agents);
        List<AgentSchedulerRegistrationSnapshot> overdue = registrations.stream()
                .filter(snapshot -> snapshot.overdueMs(nowMs) > 0L)
                .sorted(Comparator.comparingLong(
                                (AgentSchedulerRegistrationSnapshot snapshot) -> snapshot.overdueMs(nowMs))
                        .reversed()
                        .thenComparing(snapshot -> snapshot.sessionId().agentCharacterId()))
                .limit(MAX_TOP)
                .toList();
        if (overdue.isEmpty()) {
            return List.of("No central Agent registration is currently overdue.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Most overdue Agents:");
        overdue.forEach(snapshot -> lines.add("  " + agentLabel(bySession.get(snapshot.sessionId()), snapshot.sessionId())
                + " overdueMs=" + snapshot.overdueMs(nowMs) + " priority=" + snapshot.priority()
                + " paused=" + snapshot.paused() + " quiescent=" + snapshot.quiescent()));
        return List.copyOf(lines);
    }

    private static List<String> topMailboxes(List<AgentView> agents) {
        List<AgentView> queued = agents.stream()
                .filter(agent -> agent.mailboxDepth() > 0)
                .sorted(Comparator.comparingInt(AgentView::mailboxDepth).reversed()
                        .thenComparingInt(AgentView::agentId))
                .limit(MAX_TOP)
                .toList();
        if (queued.isEmpty()) {
            return List.of("No active Agent mailbox contains queued work.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Largest Agent mailboxes:");
        queued.forEach(agent -> lines.add("  " + agentLabel(agent, agent.sessionId())
                + " depth=" + agent.mailboxDepth() + " map=" + agent.mapId()));
        return List.copyOf(lines);
    }

    private static List<String> topFailures(List<AgentView> agents) {
        List<AgentView> failing = agents.stream()
                .filter(agent -> agent.recentFailures() > 0)
                .sorted(Comparator.comparingInt(AgentView::recentFailures).reversed()
                        .thenComparingInt(AgentView::agentId))
                .limit(MAX_TOP)
                .toList();
        if (failing.isEmpty()) {
            return List.of("No active Agent has a failure in its current failure window.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Most frequently failing active Agents:");
        failing.forEach(agent -> lines.add("  " + agentLabel(agent, agent.sessionId())
                + " failures=" + agent.recentFailures() + " map=" + agent.mapId()));
        return List.copyOf(lines);
    }

    private static List<String> topMaps(
            List<AgentView> agents,
            List<AgentSchedulerRegistrationSnapshot> registrations,
            long nowMs) {
        Map<AgentSessionId, AgentSchedulerRegistrationSnapshot> bySession = registrationsBySession(registrations);
        Map<Integer, MutableMapView> mutable = new HashMap<>();
        for (AgentView agent : agents) {
            MutableMapView map = mutable.computeIfAbsent(agent.mapId(), ignored -> new MutableMapView());
            map.agents++;
            map.mailboxDepth += agent.mailboxDepth();
            map.recentFailures += agent.recentFailures();
            AgentSchedulerRegistrationSnapshot registration = bySession.get(agent.sessionId());
            if (registration != null) {
                map.estimatedCostNs += registration.estimatedCostNs();
                map.overdueMs += registration.overdueMs(nowMs);
            }
        }
        List<MapView> maps = mutable.entrySet().stream()
                .map(entry -> new MapView(
                        entry.getKey(),
                        entry.getValue().agents,
                        entry.getValue().mailboxDepth,
                        entry.getValue().recentFailures,
                        entry.getValue().estimatedCostNs,
                        entry.getValue().overdueMs))
                .sorted(Comparator.comparingInt(MapView::agents).reversed()
                        .thenComparing(Comparator.comparingLong(MapView::estimatedCostNs).reversed())
                        .thenComparingInt(MapView::mapId))
                .limit(MAX_TOP)
                .toList();
        if (maps.isEmpty()) {
            return List.of("No active Agent maps are available.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Busiest Agent maps:");
        maps.forEach(map -> lines.add("  map=" + map.mapId() + " agents=" + map.agents()
                + " costUs=" + TimeUnit.NANOSECONDS.toMicros(map.estimatedCostNs())
                + " overdueMs=" + map.overdueMs() + " mailbox=" + map.mailboxDepth()
                + " failures=" + map.recentFailures()));
        return List.copyOf(lines);
    }

    private static List<String> topCapabilities(List<AgentPerformanceMonitor.SectionSnapshot> sections) {
        List<AgentPerformanceMonitor.SectionSnapshot> ranked = sections.stream()
                .filter(section -> section.count() > 0L)
                .sorted(Comparator.comparingLong(AgentPerformanceMonitor.SectionSnapshot::totalNs).reversed()
                        .thenComparing(AgentPerformanceMonitor.SectionSnapshot::section))
                .limit(MAX_TOP)
                .toList();
        if (ranked.isEmpty()) {
            return List.of("No capability timing samples are available; enable Agent performance monitoring first.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Highest-cost instrumented Agent sections:");
        ranked.forEach(section -> lines.add("  section=" + section.section()
                + " totalMs=" + formatMs(section.totalNs()) + " avgMs="
                + String.format(Locale.ROOT, "%.3f", section.avgMs()) + " maxMs="
                + String.format(Locale.ROOT, "%.3f", section.maxMs()) + " n=" + section.count()));
        return List.copyOf(lines);
    }

    private static List<String> agent(
            String[] params,
            List<AgentView> agents,
            List<AgentSchedulerRegistrationSnapshot> registrations,
            long nowMs) {
        if (params.length < 2) {
            return usage();
        }
        String query = params[1];
        AgentView match = agents.stream()
                .filter(agent -> Integer.toString(agent.agentId()).equals(query)
                        || agent.name().equalsIgnoreCase(query))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return List.of("No active Agent matches '" + query + "'.");
        }
        AgentSchedulerRegistrationSnapshot registration = registrationsBySession(registrations).get(match.sessionId());
        return List.of(formatAgent(match, registration, nowMs));
    }

    private static List<String> map(
            String[] params,
            List<AgentView> agents,
            List<AgentSchedulerRegistrationSnapshot> registrations,
            long nowMs) {
        if (params.length < 2) {
            return usage();
        }
        int mapId = Integer.parseInt(params[1]);
        List<AgentView> matches = agents.stream()
                .filter(agent -> agent.mapId() == mapId)
                .limit(MAX_TOP)
                .toList();
        if (matches.isEmpty()) {
            return List.of("No active Agent is on map " + mapId + ".");
        }
        Map<AgentSessionId, AgentSchedulerRegistrationSnapshot> bySession = registrationsBySession(registrations);
        List<String> lines = new ArrayList<>();
        lines.add("Active Agents on map " + mapId + ":");
        matches.forEach(agent -> lines.add("  " + formatAgent(agent, bySession.get(agent.sessionId()), nowMs)));
        long total = agents.stream().filter(agent -> agent.mapId() == mapId).count();
        if (total > MAX_TOP) {
            lines.add("  ... " + (total - MAX_TOP) + " more Agent(s)");
        }
        return List.copyOf(lines);
    }

    private static String formatAgent(
            AgentView agent,
            AgentSchedulerRegistrationSnapshot registration,
            long nowMs) {
        String base = agentLabel(agent, agent.sessionId()) + " map=" + agent.mapId()
                + " mailbox=" + agent.mailboxDepth() + " failures=" + agent.recentFailures()
                + " schedule=" + (agent.scheduleMode() == null ? "NONE" : agent.scheduleMode());
        if (registration == null) {
            return base;
        }
        return base + " overdueMs=" + registration.overdueMs(nowMs)
                + " costUs=" + TimeUnit.NANOSECONDS.toMicros(registration.estimatedCostNs())
                + " work=" + registration.workClass() + " mode=" + registration.simulationMode()
                + " paused=" + registration.paused() + " quiescent=" + registration.quiescent();
    }

    private static Map<AgentSessionId, AgentView> agentsBySession(List<AgentView> agents) {
        Map<AgentSessionId, AgentView> bySession = new HashMap<>();
        agents.forEach(agent -> bySession.put(agent.sessionId(), agent));
        return bySession;
    }

    private static Map<AgentSessionId, AgentSchedulerRegistrationSnapshot> registrationsBySession(
            List<AgentSchedulerRegistrationSnapshot> registrations) {
        Map<AgentSessionId, AgentSchedulerRegistrationSnapshot> bySession = new HashMap<>();
        registrations.forEach(registration -> bySession.put(registration.sessionId(), registration));
        return bySession;
    }

    private static String agentLabel(AgentView agent, AgentSessionId sessionId) {
        String name = agent == null ? "<inactive>" : agent.name();
        return name + "(#" + sessionId.agentCharacterId() + ",gen=" + sessionId.generation() + ")";
    }

    private static String formatMs(long nanoseconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0L, nanoseconds) / 1_000_000.0);
    }

    private static long micros(long nanoseconds) {
        return TimeUnit.NANOSECONDS.toMicros(Math.max(0L, nanoseconds));
    }

    private static String readyPrioritySummary(Map<AgentPriorityClass, Integer> readyByPriority) {
        if (readyByPriority.isEmpty()) {
            return "none";
        }
        return readyByPriority.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<String> usage() {
        return List.of("Usage: @agentscheduler status|shards|costs|top slow|overdue|maps|capabilities|mailboxes|failures"
                + "|agent <name|id>|map <mapId>");
    }
}
