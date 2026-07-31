package server.agents.journey;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.events.AgentEvent;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.VictoriaFirstJobMvpTestService;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-scoped journey coordinator. It may assign an existing universal plan at a
 * stage boundary; it never executes plan steps or mutates gameplay directly.
 */
public final class AgentJourneyRuntime {
    public static final String VICTORIA_LEVEL_10_TO_20 = "victoria-lv10-20";
    private static final Logger log = LoggerFactory.getLogger(AgentJourneyRuntime.class);
    private static final DateTimeFormatter RUN_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final AgentJourneyConfig CONFIG = AgentJourneyConfig.configured();
    private static final AgentJourneyStore STORE = new AgentJourneyStore(CONFIG.queueCapacity());
    private static final Map<String, AgentJourneyRun> RUNS = new ConcurrentHashMap<>();
    private static final Map<Integer, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final List<String> CAREERS = List.of(
            "warrior", "bowman", "magician", "thief-dagger", "pirate-knuckle");

    private AgentJourneyRuntime() {
    }

    public static StartResult start(
            String scenarioId,
            List<AgentRuntimeEntry> entries,
            String simulationMode,
            long nowMs) {
        if (!CONFIG.enabled()) {
            return StartResult.rejected("Agent journey observability is disabled.");
        }
        String scenario = normalizeScenario(scenarioId);
        if (!VICTORIA_LEVEL_10_TO_20.equals(scenario)) {
            return StartResult.rejected("Unknown journey scenario '" + scenarioId + "'.");
        }
        String mode;
        try {
            mode = normalizeMode(simulationMode);
        } catch (IllegalArgumentException invalid) {
            return StartResult.rejected(invalid.getMessage());
        }
        List<AgentRuntimeEntry> selected = entries == null ? List.of()
                : entries.stream()
                .filter(entry -> AgentRuntimeIdentityRuntime.bot(entry) != null)
                .toList();
        if (selected.isEmpty()) {
            return StartResult.rejected("No live Agents were available for the journey.");
        }
        if (selected.size() > CONFIG.maxDetailedAgents()) {
            return StartResult.rejected("Journey requested " + selected.size()
                    + " Agents but the detailed-run ceiling is "
                    + CONFIG.maxDetailedAgents() + ".");
        }
        for (AgentRuntimeEntry entry : selected) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            Binding existing = BINDINGS.get(agent.getId());
            if (existing != null) {
                return StartResult.rejected(
                        agent.getName() + " is already tracked by journey " + existing.runId + ".");
            }
        }
        String runId = scenario + "-" + RUN_TIME.format(Instant.ofEpochMilli(nowMs))
                + "-" + Long.toUnsignedString(nowMs, 36);
        List<AgentJourneyManifest.Participant> participants = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            Character agent = AgentRuntimeIdentityRuntime.bot(selected.get(index));
            participants.add(new AgentJourneyManifest.Participant(
                    agent.getId(), agent.getName(), CAREERS.get(index % CAREERS.size())));
        }
        AgentJourneyManifest manifest = new AgentJourneyManifest(
                1, runId, scenario, mode, nowMs, 20, participants);
        try {
            STORE.createRun(manifest);
        } catch (IOException failure) {
            return StartResult.rejected("Could not create journey evidence: " + failure.getMessage());
        }
        AgentJourneyRun run = new AgentJourneyRun(manifest, CONFIG, STORE);
        RUNS.put(runId, run);
        for (int index = 0; index < selected.size(); index++) {
            AgentRuntimeEntry entry = selected.get(index);
            AgentJourneyManifest.Participant participant = participants.get(index);
            Binding binding = new Binding(runId, nowMs);
            BINDINGS.put(participant.characterId(), binding);
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                try {
                    VictoriaFirstJobMvpTestService.resetAndStart(
                            entry, participant.career(), "lv10",
                            VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_1,
                            System.currentTimeMillis());
                    binding.initialized = true;
                } catch (IOException | RuntimeException failure) {
                    binding.initialized = true;
                    binding.failureReason = "fixture start failed: " + failure.getMessage();
                    fail(run, participant.characterId(), binding.failureReason,
                            System.currentTimeMillis());
                    log.warn("Journey {} could not initialize {}", runId,
                            participant.characterName(), failure);
                }
                return null;
            });
        }
        return new StartResult(true, runId, selected.size(),
                STORE.runDirectory(runId).toString(), "");
    }

    public static void tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return;
        }
        Binding binding = BINDINGS.get(agent.getId());
        if (binding == null) {
            return;
        }
        AgentJourneyRun run = RUNS.get(binding.runId);
        if (run == null || run.complete()) {
            BINDINGS.remove(agent.getId(), binding);
            return;
        }
        if (!binding.initialized || !binding.failureReason.isBlank()) {
            return;
        }
        if (nowMs >= binding.nextSampleAtMs) {
            binding.nextSampleAtMs = nowMs + CONFIG.sampleIntervalMs();
            run.sample(AgentJourneySnapshot.capture(entry, agent, nowMs));
        }
        AgentPlanExecutionStatus planStatus = AgentUniversalPlanRuntime.status(entry);
        if (planStatus == AgentPlanExecutionStatus.BLOCKED
                || planStatus == AgentPlanExecutionStatus.FAILED) {
            String reason = "universal plan " + planStatus.name().toLowerCase(Locale.ROOT);
            binding.failureReason = reason;
            fail(run, agent.getId(), reason, nowMs);
            return;
        }
        if (AgentUniversalPlanRuntime.active(entry)) {
            return;
        }
        if (agent.getLevel() >= run.manifest().targetLevel()) {
            run.markAgentCompleted(agent.getId(), nowMs);
            BINDINGS.remove(agent.getId(), binding);
            if (run.complete()) {
                finalizeReport(run, nowMs, false);
            }
            return;
        }
        if (agent.getLevel() >= 15 && agent.getJob().getId() != 0
                && !binding.trainingStarted) {
            boolean started = AgentUniversalPlanRuntime.start(
                    entry, agent, "victoria-training",
                    new AgentPlanStartRequest(Map.of(
                            "targetLevel", run.manifest().targetLevel(),
                            "questsEnabled", true), null), nowMs);
            if (started) {
                binding.trainingStarted = true;
            } else {
                binding.trainingStartAttempts++;
                if (binding.trainingStartAttempts >= 5) {
                    binding.failureReason = "universal Victoria training rejected five stage-boundary starts";
                    fail(run, agent.getId(), binding.failureReason, nowMs);
                }
            }
        }
    }

    public static void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        Binding binding = BINDINGS.get(event.agentId());
        if (binding == null) {
            return;
        }
        AgentJourneyRun run = RUNS.get(binding.runId);
        if (run != null) {
            run.onEvent(event);
        }
    }

    public static StatusResult status(String runId) {
        AgentJourneyRun run = resolveRun(runId);
        if (run == null) {
            return StatusResult.missing(runId);
        }
        return new StatusResult(true, run.manifest().runId(), run.status().name(),
                run.manifest().scenarioId(), run.manifest().targetLevel(),
                run.traceViews(), STORE.runDirectory(run.manifest().runId()).toString());
    }

    public static AgentJourneyTraceView agent(String runId, String agentName) {
        AgentJourneyRun run = resolveRun(runId);
        if (run == null || agentName == null) {
            return null;
        }
        return run.traceViews().stream()
                .filter(view -> view.agentName().equalsIgnoreCase(agentName))
                .findFirst().orElse(null);
    }

    public static ReportResult report(String runId, long nowMs) {
        AgentJourneyRun run = resolveRun(runId);
        if (run == null) {
            return ReportResult.missing(runId);
        }
        return finalizeReport(run, nowMs, true);
    }

    public static ReportResult stop(String runId, long nowMs) {
        AgentJourneyRun run = resolveRun(runId);
        if (run == null) {
            return ReportResult.missing(runId);
        }
        run.stop(nowMs);
        for (AgentJourneyManifest.Participant participant : run.manifest().participants()) {
            Binding binding = BINDINGS.remove(participant.characterId());
            if (binding == null) {
                continue;
            }
            // Stopping an experiment should stop only the universal plan it dispatched.
            AgentRuntimeEntry entry =
                    server.agents.runtime.AgentRuntimeRegistry.findByAgentCharacterId(
                            participant.characterId());
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            if (entry != null && agent != null) {
                AgentMailboxRuntime.dispatch(entry, ignored -> {
                    AgentUniversalPlanRuntime.cancel(
                            entry, agent, "journey stopped by operator", System.currentTimeMillis());
                    return null;
                });
            }
        }
        return finalizeReport(run, nowMs, true);
    }

    private static ReportResult finalizeReport(
            AgentJourneyRun run, long nowMs, boolean awaitWrite) {
        AgentJourneyReport report = run.report(nowMs);
        try {
            STORE.writeReportAsync(run.manifest().runId(), report);
            if (awaitWrite) {
                STORE.awaitDrained(2_000L);
            }
            if (run.complete()) {
                run.manifest().participants().forEach(
                        participant -> BINDINGS.remove(participant.characterId()));
            }
            return new ReportResult(true, run.manifest().runId(),
                    STORE.runDirectory(run.manifest().runId())
                            .resolve("summaries").resolve("report.md").toString(), "");
        } catch (RuntimeException failure) {
            return new ReportResult(false, run.manifest().runId(), "",
                    "Could not write journey report: " + failure.getMessage());
        }
    }

    private static void fail(
            AgentJourneyRun run, int agentId, String reason, long nowMs) {
        run.markAgentFailed(agentId, reason, nowMs);
        BINDINGS.remove(agentId);
        if (run.complete()) {
            finalizeReport(run, nowMs, false);
        }
    }

    private static AgentJourneyRun resolveRun(String requested) {
        if (requested != null && !requested.isBlank()) {
            return RUNS.get(requested.trim());
        }
        return RUNS.values().stream()
                .max((left, right) -> Long.compare(
                        left.manifest().startedAtMs(), right.manifest().startedAtMs()))
                .orElse(null);
    }

    private static String normalizeScenario(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMode(String value) {
        String mode = value == null || value.isBlank()
                ? "full" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of("off", "light", "full").contains(mode)) {
            throw new IllegalArgumentException("Journey mode must be off, light, or full.");
        }
        return mode;
    }

    private static final class Binding {
        private final String runId;
        private volatile long nextSampleAtMs;
        private volatile boolean initialized;
        private volatile boolean trainingStarted;
        private volatile int trainingStartAttempts;
        private volatile String failureReason = "";

        private Binding(String runId, long nowMs) {
            this.runId = runId;
            this.nextSampleAtMs = nowMs;
        }
    }

    public record StartResult(
            boolean started,
            String runId,
            int participants,
            String directory,
            String reason) {
        private static StartResult rejected(String reason) {
            return new StartResult(false, "", 0, "", reason);
        }
    }

    public record StatusResult(
            boolean found,
            String runId,
            String status,
            String scenarioId,
            int targetLevel,
            List<AgentJourneyTraceView> agents,
            String directory) {
        private static StatusResult missing(String runId) {
            return new StatusResult(false, runId == null ? "" : runId,
                    "", "", 0, List.of(), "");
        }
    }

    public record ReportResult(boolean written, String runId, String path, String reason) {
        private static ReportResult missing(String runId) {
            return new ReportResult(false, runId == null ? "" : runId,
                    "", "Journey run was not found.");
        }
    }
}
