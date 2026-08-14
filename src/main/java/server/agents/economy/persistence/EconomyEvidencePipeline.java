package server.agents.economy.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One operational evidence cycle: relay, exact ingestion, rebuildable projections, then audit. */
public final class EconomyEvidencePipeline {
    private final EconomyOutboxRelay relay;
    private final JdbcCosmicEconomicEventIngestor ingestor;
    private final JdbcEconomyProjectionService projections;
    private final JdbcEconomyInvariantAuditor auditor;

    public EconomyEvidencePipeline(EconomyOutboxRelay relay, JdbcCosmicEconomicEventIngestor ingestor,
                                   JdbcEconomyProjectionService projections,
                                   JdbcEconomyInvariantAuditor auditor) {
        this.relay = Objects.requireNonNull(relay); this.ingestor = Objects.requireNonNull(ingestor);
        this.projections = Objects.requireNonNull(projections); this.auditor = Objects.requireNonNull(auditor);
    }

    public Result process(UUID runId, Instant logicalAt, int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batch size must be positive");
        EconomyOutboxRelay.Result relayed = relay.relay(batchSize);
        JdbcCosmicEconomicEventIngestor.Result ingested = ingestor.ingest(batchSize);
        JdbcEconomyProjectionService.Result projected = projections.rebuild(runId);
        JdbcEconomyInvariantAuditor.Audit audit = auditor.audit(runId, logicalAt);
        return new Result(relayed, ingested, projected, audit);
    }

    public record Result(EconomyOutboxRelay.Result relay,
                         JdbcCosmicEconomicEventIngestor.Result ingestion,
                         JdbcEconomyProjectionService.Result projections,
                         JdbcEconomyInvariantAuditor.Audit audit) { }
}
