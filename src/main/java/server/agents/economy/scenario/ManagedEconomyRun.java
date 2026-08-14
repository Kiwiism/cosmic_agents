package server.agents.economy.scenario;

import server.agents.economy.persistence.EconomyEvidencePipeline;
import server.agents.economy.persistence.SimulationRunRepository;

import java.time.Instant;
import java.util.Objects;

/** Operational facade: every advance checkpoints, ingests evidence, rebuilds views, and audits. */
public final class ManagedEconomyRun {
    private final EconomyRunApplication application;
    private final EconomyEvidencePipeline evidence;
    private final SimulationRunRepository runs;
    private final int batchSize;
    private final boolean stopOnInvariantViolation;

    public ManagedEconomyRun(EconomyRunApplication application, EconomyEvidencePipeline evidence,
                             SimulationRunRepository runs, int batchSize, boolean stopOnInvariantViolation) {
        this.application = Objects.requireNonNull(application); this.evidence = Objects.requireNonNull(evidence);
        this.runs = Objects.requireNonNull(runs);
        if (batchSize <= 0) throw new IllegalArgumentException("batch size must be positive");
        this.batchSize = batchSize; this.stopOnInvariantViolation = stopOnInvariantViolation;
        application.onCheckpoint(() -> runs.saveCheckpoint(application.checkpoint()));
    }

    public AdvanceResult advanceDays(long days) {
        if (days < 0) throw new IllegalArgumentException("economy runs cannot move backward");
        return finishAdvance(application.advanceDays(days));
    }
    public AdvanceResult advanceTo(Instant logicalAt) {
        if (logicalAt.isBefore(application.now())) throw new IllegalArgumentException("economy runs cannot move backward");
        return finishAdvance(application.advanceTo(logicalAt));
    }

    public EconomyEvidencePipeline.Result checkpoint(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        runs.saveCheckpoint(application.checkpoint());
        EconomyEvidencePipeline.Result processed = evidence.process(application.runId(), application.now(), batchSize);
        String persistedStatus = processed.audit().clean() ? status : "INVARIANT_VIOLATION";
        runs.updateLogicalTime(application.runId(), application.now(), persistedStatus);
        if (!processed.audit().clean() && stopOnInvariantViolation)
            throw new IllegalStateException("economy invariant violation: " + processed.audit().violations());
        return processed;
    }

    private AdvanceResult finishAdvance(SimulationRunEngine.AdvanceSummary advance) {
        String status = advance.waitingExternalAction() ? "WAITING_PHYSICAL_ACTION" : "RUNNING";
        EconomyEvidencePipeline.Result processed = checkpoint(status);
        if (!processed.audit().clean()) status = "INVARIANT_VIOLATION";
        return new AdvanceResult(advance, processed, status);
    }

    public EconomyRunApplication application() { return application; }
    public record AdvanceResult(SimulationRunEngine.AdvanceSummary advance,
                                EconomyEvidencePipeline.Result evidence, String status) { }
}
