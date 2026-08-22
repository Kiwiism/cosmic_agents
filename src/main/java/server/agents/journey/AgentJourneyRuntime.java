package server.agents.journey;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.events.AgentEvent;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.capabilities.quest.AmherstTestResetService;
import server.agents.capabilities.quest.AmherstTestRuntimeResetService;
import server.agents.capabilities.supplies.AgentResourceAutonomyState;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.mapleisland.AgentMapleIslandPlanRuntime;
import server.agents.plans.AgentPlanExecutionStatus;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerBuildBundleService;
import server.agents.progression.AgentCareerProgressionState;
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
 * Run-scoped journey coordinator. A scenario may apply a declared admission
 * fixture through its capability service and assign an existing universal plan
 * at a stage boundary; this coordinator never executes plan steps directly.
 */
public final class AgentJourneyRuntime {
    public static final String VICTORIA_LEVEL_10_TO_20 = "victoria-lv10-20";
    public static final String VICTORIA_LEVEL_1_TO_21 = "victoria-lv1-21";
    private static final Logger log = LoggerFactory.getLogger(AgentJourneyRuntime.class);
    private static final DateTimeFormatter RUN_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final AgentJourneyConfig CONFIG = AgentJourneyConfig.configured();
    private static final long TERMINAL_STALL_MIN_MS = config.AgentTuning.longValue(
            "server.agents.journey.AgentJourneyRuntime.TERMINAL_STALL_MIN_MS");
    private static final AgentJourneyStore STORE = new AgentJourneyStore(CONFIG.queueCapacity());
    private static final Map<String, AgentJourneyRun> RUNS = new ConcurrentHashMap<>();
    private static final Map<Integer, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final List<String> LEGACY_CAREERS = List.of(
            "warrior", "bowman", "magician", "thief-dagger", "pirate-knuckle");
    private static final List<String> CLEAN_START_CAREERS = List.of(
            "warrior", "bowman", "magician", "thief-dagger", "pirate-gun",
            "warrior", "bowman", "magician", "thief-claw", "pirate-knuckle",
            "warrior", "bowman", "magician", "thief-dagger", "pirate-gun",
            "warrior", "bowman", "magician", "thief-claw", "pirate-knuckle",
            "warrior", "bowman", "magician", "thief-dagger", "pirate-gun");

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
        if (!VICTORIA_LEVEL_10_TO_20.equals(scenario)
                && !VICTORIA_LEVEL_1_TO_21.equals(scenario)) {
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
        if (VICTORIA_LEVEL_1_TO_21.equals(scenario)) {
            Character invalid = selected.stream().map(AgentRuntimeIdentityRuntime::bot)
                    .filter(agent -> agent.getJob().getId() != 0
                            || agent.getMapId() >= 100_000_000)
                    .findFirst().orElse(null);
            if (invalid != null) {
                return StartResult.rejected("Clean-start journey requires live beginner "
                        + "Agents on Maple Island; " + invalid.getName() + " is Lv"
                        + invalid.getLevel() + " job=" + invalid.getJob().getId()
                        + " map=" + invalid.getMapId() + '.');
            }
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
        List<String> careers = VICTORIA_LEVEL_1_TO_21.equals(scenario)
                ? CLEAN_START_CAREERS : LEGACY_CAREERS;
        for (int index = 0; index < selected.size(); index++) {
            Character agent = AgentRuntimeIdentityRuntime.bot(selected.get(index));
            participants.add(new AgentJourneyManifest.Participant(
                    agent.getId(), agent.getName(), careers.get(index % careers.size())));
        }
        int targetLevel = VICTORIA_LEVEL_1_TO_21.equals(scenario) ? 21 : 20;
        AgentJourneyManifest manifest = new AgentJourneyManifest(
                1, runId, scenario, mode, nowMs, targetLevel, participants);
        try {
            STORE.createRun(manifest);
        } catch (IOException failure) {
            return StartResult.rejected("Could not create journey evidence: " + failure.getMessage());
        }
        AgentJourneyRun run = new AgentJourneyRun(manifest, CONFIG, STORE);
        RUNS.put(runId, run);
        for (int index = 0; index < selected.size(); index++) {
            AgentRuntimeEntry entry = selected.get(index);
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            AgentJourneyManifest.Participant participant = participants.get(index);
            Binding binding = new Binding(runId, nowMs);
            BINDINGS.put(participant.characterId(), binding);
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                try {
                    if (VICTORIA_LEVEL_1_TO_21.equals(scenario)) {
                        AgentCareerBuildBundle bundle =
                                VictoriaFirstJobMvpTestService.resolveBundle(participant.career());
                        initializeCleanMapleIslandStart(entry, agent, nowMs);
                        AgentCareerBuildBundleService.assignForTest(
                                entry, bundle.bundleId(), nowMs);
                        if (!AgentUniversalPlanRuntime.start(entry, agent,
                                "maple-island-full-mvp", AgentPlanStartRequest.EMPTY, nowMs)) {
                            throw new IllegalStateException(
                                    "clean Maple Island plan start was rejected");
                        }
                        binding.careerAssigned = true;
                    } else {
                        VictoriaFirstJobMvpTestService.resetAndStart(
                                entry, participant.career(), "lv10",
                                VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_1,
                                System.currentTimeMillis());
                    }
                    binding.initialized = true;
                } catch (Exception failure) {
                    binding.initialized = true;
                    binding.failureReason = "fixture start failed: " + failure.getMessage();
                    fail(run, entry, participant.characterId(), binding.failureReason,
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

    private static void initializeCleanMapleIslandStart(
            AgentRuntimeEntry entry, Character agent, long nowMs) throws Exception {
        AgentUniversalPlanRuntime.cancel(
                entry, agent, "Journey clean-start admission", nowMs);
        AmherstTestRuntimeResetService.reset(entry, agent, nowMs);
        AmherstTestResetResult reset = AmherstTestResetService
                .showcaseHarness(true, agent.getName())
                .reset(new AmherstTestResetRequest(
                        agent.getId(), agent.getName(),
                        AmherstTestResetMode.CLEAN_LV1_START, 0));
        if (!reset.allowed()) {
            throw new IllegalStateException("clean level-1 reset was blocked: "
                    + reset.message());
        }
        if (!AgentMapleIslandPlanRuntime.clearSession(entry)) {
            throw new IllegalStateException(
                    "previous Maple Island capability is still closing");
        }
        var card = AgentMapleIslandPlanRuntime.fullCard();
        AgentMapleIslandPlanRuntime.defaultStore().delete(card.planId(), agent.getId());
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        entry.capabilityStates().require(AgentResourceAutonomyState.STATE_KEY)
                .requireSelfSustaining();
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
            clearRunResourceGuard(entry);
            BINDINGS.remove(agent.getId(), binding);
            return;
        }
        if (!binding.initialized || !binding.failureReason.isBlank()) {
            return;
        }
        String supplyStall = entry.capabilityStates()
                .find(AgentSupplyProcurementState.STATE_KEY)
                .map(AgentSupplyProcurementState::stalledReason).orElse("");
        if (!supplyStall.isBlank()) {
            binding.failureReason = "resource recovery stalled: " + supplyStall;
            stall(run, entry, agent.getId(), binding.failureReason, nowMs);
            return;
        }
        if (nowMs >= binding.nextSampleAtMs) {
            binding.nextSampleAtMs = nowMs + CONFIG.sampleIntervalMs();
            run.sample(AgentJourneySnapshot.capture(entry, agent, nowMs));
            AgentJourneyTraceView trace = run.traceViews().stream()
                    .filter(candidate -> candidate.agentId() == agent.getId())
                    .findFirst().orElse(null);
            long terminalStallAfterMs = Math.max(
                    TERMINAL_STALL_MIN_MS, CONFIG.mapDwellAfterMs() * 3L);
            if (trace != null && trace.lastProgressAtMs() > 0L
                    && nowMs - trace.lastProgressAtMs() >= terminalStallAfterMs) {
                String reason = "no semantic journey progress for "
                        + (nowMs - trace.lastProgressAtMs()) + "ms after bounded recovery";
                binding.failureReason = reason;
                stall(run, entry, agent.getId(), reason, nowMs);
                return;
            }
        }
        AgentPlanExecutionStatus planStatus = AgentUniversalPlanRuntime.status(entry);
        if (planStatus == AgentPlanExecutionStatus.BLOCKED
                || planStatus == AgentPlanExecutionStatus.FAILED) {
            var outcome = AgentUniversalPlanRuntime.outcome(entry);
            String detail = outcome == null ? "" : outcome.reason();
            String reason = "universal plan " + planStatus.name().toLowerCase(Locale.ROOT)
                    + (detail.isBlank() ? "" : ": " + detail);
            binding.failureReason = reason;
            stall(run, entry, agent.getId(), reason, nowMs);
            return;
        }
        if (AgentUniversalPlanRuntime.active(entry)) {
            return;
        }
        if (agent.getLevel() >= run.manifest().targetLevel()) {
            run.markAgentCompleted(agent.getId(), nowMs);
            clearRunResourceGuard(entry);
            BINDINGS.remove(agent.getId(), binding);
            if (run.complete()) {
                finalizeReport(run, nowMs, false);
            }
            return;
        }
        if (VICTORIA_LEVEL_1_TO_21.equals(run.manifest().scenarioId())
                && agent.getJob().getId() == 0 && agent.getMapId() < 100_000_000) {
            if (agent.getMapId() != 2_000_000) {
                return;
            }
            AgentCareerProgressionState career = entry.capabilityStates()
                    .require(AgentCareerProgressionState.STATE_KEY);
            if (career.bundle() == null || !binding.careerAssigned) {
                binding.failureReason = "clean-start career assignment was lost before Victoria handoff";
                stall(run, entry, agent.getId(), binding.failureReason, nowMs);
                return;
            }
            career.reset(career.bundle(),
                    AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                    "live-lv1", AgentCareerProgressionState.Stage.TRAVEL_TO_LITH, nowMs);
            if (AgentUniversalPlanRuntime.start(entry, agent, "victoria-level15-mvp",
                    AgentPlanStartRequest.EMPTY, nowMs)) {
                binding.victoriaStarted = true;
                return;
            }
            binding.stageStartAttempts++;
            if (binding.stageStartAttempts >= 5) {
                binding.failureReason = "Victoria handoff rejected five clean stage-boundary starts";
                stall(run, entry, agent.getId(), binding.failureReason, nowMs);
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
                    fail(run, entry, agent.getId(), binding.failureReason, nowMs);
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

    /** Closes an enrolled participant when its runtime session disappears mid-journey. */
    public static void onSessionClosed(
            AgentRuntimeEntry entry, String reason, long nowMs) {
        if (entry == null) {
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return;
        }
        Binding binding = BINDINGS.get(agent.getId());
        if (binding == null) {
            return;
        }
        AgentJourneyRun run = RUNS.get(binding.runId);
        if (run == null || run.complete()) {
            clearRunResourceGuard(entry);
            BINDINGS.remove(agent.getId(), binding);
            return;
        }
        String detail = reason == null || reason.isBlank()
                ? "Agent runtime session closed before the journey reached a terminal target"
                : "Agent runtime session closed: " + reason.trim();
        binding.failureReason = detail;
        stall(run, entry, agent.getId(), detail, nowMs);
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
                    clearRunResourceGuard(entry);
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
            AgentJourneyRun run, AgentRuntimeEntry entry,
            int agentId, String reason, long nowMs) {
        run.markAgentFailed(agentId, reason, nowMs);
        clearRunResourceGuard(entry);
        BINDINGS.remove(agentId);
        if (run.complete()) {
            finalizeReport(run, nowMs, false);
        }
    }

    private static void stall(
            AgentJourneyRun run, AgentRuntimeEntry entry,
            int agentId, String reason, long nowMs) {
        run.markAgentStalled(agentId, reason, nowMs);
        clearRunResourceGuard(entry);
        BINDINGS.remove(agentId);
        if (run.complete()) {
            finalizeReport(run, nowMs, false);
        }
    }

    private static void clearRunResourceGuard(AgentRuntimeEntry entry) {
        if (entry == null) return;
        entry.capabilityStates().find(AgentResourceAutonomyState.STATE_KEY)
                .ifPresent(AgentResourceAutonomyState::clear);
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
        if (!List.of("off", "light", "full", "decisions").contains(mode)) {
            throw new IllegalArgumentException(
                    "Journey mode must be off, light, full, or decisions.");
        }
        return mode;
    }

    private static final class Binding {
        private final String runId;
        private volatile long nextSampleAtMs;
        private volatile boolean initialized;
        private volatile boolean careerAssigned;
        private volatile boolean victoriaStarted;
        private volatile boolean trainingStarted;
        private volatile int stageStartAttempts;
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
