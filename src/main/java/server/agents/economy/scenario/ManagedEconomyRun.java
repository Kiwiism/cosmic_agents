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
    }

    public AdvanceResult advanceDays(long days) {
        if (days < 0) throw new IllegalArgumentException("economy runs cannot move backward");
        return finishAdvance(application.advanceDays(days));
    }
    public AdvanceResult advanceTo(Instant logicalAt) {
        if (logicalAt.isBefore(application.now())) throw new IllegalArgumentException("economy runs cannot move backward");
        return finishAdvance(application.advanceTo(logicalAt));
    }

    private AdvanceResult finishAdvance(SimulationRunEngine.AdvanceSummary advance) {
        runs.saveCheckpoint(application.checkpoint());
        EconomyEvidencePipeline.Result processed = evidence.process(application.runId(), application.now(), batchSize);
        String status = advance.waitingExternalAction() ? "WAITING_PHYSICAL_ACTION" : "RUNNING";
        if (!processed.audit().clean() && stopOnInvariantViolation) status = "INVARIANT_VIOLATION";
        runs.updateLogicalTime(application.runId(), application.now(), status);
        if (!processed.audit().clean() && stopOnInvariantViolation)
            throw new IllegalStateException("economy invariant violation: " + processed.audit().violations());
        return new AdvanceResult(advance, processed, status);
    }

    public EconomyRunApplication application() { return application; }
    public record AdvanceResult(SimulationRunEngine.AdvanceSummary advance,
                                EconomyEvidencePipeline.Result evidence, String status) { }
}
