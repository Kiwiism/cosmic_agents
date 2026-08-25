package server.agents.economy.scenario;

import server.agents.economy.persistence.EconomyEvidencePipeline;
import server.agents.economy.persistence.SimulationRunRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Operational facade: every advance checkpoints, ingests evidence, rebuilds views, and audits. */
public final class ManagedEconomyRun {
    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "FAILED", "STOPPED");
    private final EconomyRunApplication application;
    private final EconomyEvidencePipeline evidence;
    private final SimulationRunRepository runs;
    private final int batchSize;
    private final boolean stopOnInvariantViolation;
    private final EconomyDayCloseReconciler dayCloseReconciler;
    private String status;
    private boolean terminal;

    public ManagedEconomyRun(EconomyRunApplication application, EconomyEvidencePipeline evidence,
                             SimulationRunRepository runs, int batchSize, boolean stopOnInvariantViolation) {
        this(application, evidence, runs, batchSize, stopOnInvariantViolation, "CREATED",
                EconomyDayCloseReconciler.ledgerOnly());
    }

    public ManagedEconomyRun(EconomyRunApplication application, EconomyEvidencePipeline evidence,
                             SimulationRunRepository runs, int batchSize, boolean stopOnInvariantViolation,
                             String initialStatus) {
        this(application, evidence, runs, batchSize, stopOnInvariantViolation, initialStatus,
                EconomyDayCloseReconciler.ledgerOnly());
    }

    public ManagedEconomyRun(EconomyRunApplication application, EconomyEvidencePipeline evidence,
                             SimulationRunRepository runs, int batchSize, boolean stopOnInvariantViolation,
                             String initialStatus, EconomyDayCloseReconciler dayCloseReconciler) {
        this.application = Objects.requireNonNull(application);
        this.evidence = Objects.requireNonNull(evidence);
        this.runs = Objects.requireNonNull(runs);
        if (batchSize <= 0) throw new IllegalArgumentException("batch size must be positive");
        if (initialStatus == null || initialStatus.isBlank())
            throw new IllegalArgumentException("initial status is required");
        this.batchSize = batchSize;
        this.stopOnInvariantViolation = stopOnInvariantViolation;
        this.dayCloseReconciler = Objects.requireNonNull(dayCloseReconciler);
        this.status = initialStatus;
        this.terminal = TERMINAL_STATUSES.contains(initialStatus);
        application.onCheckpoint(() -> runs.saveCheckpoint(application.checkpoint()));
    }

    public AdvanceResult advanceDays(long days) {
        requireActive();
        if (days < 0) throw new IllegalArgumentException("economy runs cannot move backward");
        Instant requested = application.now().plus(Duration.ofDays(days));
        return finishAdvance(application.advanceTo(min(requested, application.targetAt())));
    }

    /** Completes the current run-relative 24-hour day and persists a clean close manifest. */
    public AdvanceResult advanceDay() {
        requireActive();
        if ("DAY_CLOSE_BLOCKED".equals(status)) return retryDayClose();
        Instant boundary = min(application.nextDayBoundary(), application.targetAt());
        return finishDayAdvance(application.advanceToDayBoundary(boundary), boundary);
    }

    /** Resumes a previously interrupted physical advance toward the same day boundary. */
    public AdvanceResult advanceToDayBoundary(Instant boundary) {
        requireActive();
        if ("DAY_CLOSE_BLOCKED".equals(status) && boundary.equals(application.now()))
            return retryDayClose();
        return finishDayAdvance(application.advanceToDayBoundary(boundary), boundary);
    }

    /** Re-runs reconciliation at a blocked boundary without moving logical time. */
    public AdvanceResult retryDayClose() {
        requireActive();
        if (!"DAY_CLOSE_BLOCKED".equals(status))
            throw new IllegalStateException("day close is not blocked");
        Instant boundary = application.now();
        long elapsedSeconds = Duration.between(application.logicalStart(), boundary).getSeconds();
        if (elapsedSeconds <= 0 || Math.floorMod(elapsedSeconds, Duration.ofDays(1).getSeconds()) != 0)
            throw new IllegalStateException("run is not at a logical day boundary");
        return finishDayAdvance(new SimulationRunEngine.AdvanceSummary(boundary, 0, 0, 0), boundary);
    }

    public AdvanceResult advanceTo(Instant logicalAt) {
        requireActive();
        if (logicalAt.isBefore(application.now()))
            throw new IllegalArgumentException("economy runs cannot move backward");
        return finishAdvance(application.advanceTo(min(logicalAt, application.targetAt())));
    }

    public EconomyEvidencePipeline.Result checkpoint(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank())
            throw new IllegalArgumentException("status is required");
        runs.saveCheckpoint(application.checkpoint());
        EconomyEvidencePipeline.Result processed = evidence.process(
                application.runId(), application.now(), batchSize);
        String persistedStatus = processed.audit().clean() ? requestedStatus : "INVARIANT_VIOLATION";
        runs.updateLogicalTime(application.runId(), application.now(), persistedStatus);
        status = persistedStatus;
        terminal = TERMINAL_STATUSES.contains(persistedStatus);
        if (!processed.audit().clean() && stopOnInvariantViolation)
            throw new IllegalStateException("economy invariant violation: " + processed.audit().violations());
        return processed;
    }

    public EconomyEvidencePipeline.Result audit() {
        requireActive();
        return checkpoint(status);
    }

    public EconomyEvidencePipeline.Result complete() {
        requireActive();
        if (application.now().isBefore(application.targetAt()))
            throw new IllegalStateException("economy run has not reached its configured logical horizon");
        return checkpoint("COMPLETED");
    }

    public EconomyEvidencePipeline.Result fail(String reason) {
        requireActive();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("failure reason is required");
        runs.saveCheckpoint(application.checkpoint());
        try {
            EconomyEvidencePipeline.Result processed = evidence.process(
                    application.runId(), application.now(), batchSize);
            runs.updateStatus(application.runId(), application.now(), "FAILED", reason);
            status = "FAILED";
            terminal = true;
            return processed;
        } catch (RuntimeException failure) {
            runs.updateStatus(application.runId(), application.now(), "FAILED",
                    reason + "; final evidence failure: " + failure.getClass().getSimpleName());
            status = "FAILED";
            terminal = true;
            throw failure;
        }
    }

    private AdvanceResult finishAdvance(SimulationRunEngine.AdvanceSummary advance) {
        String nextStatus = advance.waitingExternalAction() ? "WAITING_PHYSICAL_ACTION"
                : !advance.reachedAt().isBefore(application.targetAt()) ? "COMPLETED" : "RUNNING";
        EconomyEvidencePipeline.Result processed = checkpoint(nextStatus);
        if (!processed.audit().clean()) nextStatus = "INVARIANT_VIOLATION";
        return new AdvanceResult(advance, processed, nextStatus);
    }

    private AdvanceResult finishDayAdvance(SimulationRunEngine.AdvanceSummary advance, Instant boundary) {
        if (advance.waitingExternalAction()) return finishAdvance(advance);
        if (!advance.reachedAt().equals(boundary))
            throw new IllegalStateException("day advance stopped before its boundary");
        SimulationRunEngine.RunCheckpoint checkpoint = application.checkpoint();
        runs.saveCheckpoint(checkpoint);
        EconomyEvidencePipeline.Result processed = evidence.process(
                application.runId(), application.now(), batchSize);
        EconomyDayCloseReconciler.Result holdings = dayCloseReconciler.reconcile(
                application.runId(), application.agents(), application.now());
        if (processed.relay().failed() > 0 || processed.ingestion().quarantined() > 0
                || !processed.audit().clean() || !holdings.clean()) {
            runs.updateLogicalTime(application.runId(), application.now(), "DAY_CLOSE_BLOCKED");
            status = "DAY_CLOSE_BLOCKED";
            return new AdvanceResult(advance, processed, status);
        }

        int dayIndex = Math.toIntExact(Duration.between(application.logicalStart(), boundary).toDays());
        Instant dayStartedAt = boundary.minus(Duration.ofDays(1));
        String checkpointHash = new server.agents.economy.persistence.RunCheckpointCodec()
                .encode(checkpoint).sha256();
        runs.saveDayClose(new SimulationRunRepository.DayCloseRecord(application.runId(), dayIndex,
                dayStartedAt, boundary, checkpointHash, processed.relay().delivered(),
                processed.relay().failed(), processed.ingestion().ingested(),
                processed.ingestion().quarantined(), true, holdings.violations().size()));
        status = boundary.isBefore(application.targetAt()) ? "DAY_CLOSED" : "COMPLETED";
        terminal = TERMINAL_STATUSES.contains(status);
        runs.updateLogicalTime(application.runId(), application.now(), status);
        return new AdvanceResult(advance, processed, status);
    }

    public EconomyRunApplication application() { return application; }
    public String status() { return status; }

    private void requireActive() {
        if (terminal) throw new IllegalStateException("economy run is terminal: " + status);
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    public record AdvanceResult(SimulationRunEngine.AdvanceSummary advance,
                                EconomyEvidencePipeline.Result evidence, String status) { }
}
